package com.dell.jobot;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.val;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class HttpUrlStreamHandler
implements RawUrlStreamHandler {

	private static final String OUTPUT_DIR = outputDir() + File.separator + ".jobot";
	private static final String LINKS_FILE_NAME = "links.txt";

	private final ExecutorService executor;
	private final Predicate<URL> urlFilter;
	private final ExecutorService urlHandlingExecutor = Main.setupExecutor();

	HttpUrlStreamHandler(final ExecutorService executor, final Predicate<URL> urlFilter) {
		this.executor = executor;
		this.urlFilter = urlFilter;
	}

	// TODO investigate an option of changing interface to use String rawUrl instead of Stream<String> inStream
    //   as a parameter here. I briefly tried it and code became cleaner but in the same time preliminary
    //   benchmarking has shown some worrying signs of possible performance degradation, so I decided to postpone
    //   it until I have time for more thorough check.
	@Override
	public void handle(final URL parent, final @NonNull Stream<String> inStream) {
		// IMPL NOTE handle work asynchronously in order to avoid blocking caller code.
		urlHandlingExecutor.submit(() -> obtainLinks(parent, inStream));
	}

	private void obtainLinks(final URL parent, @NonNull Stream<String> inStream) {
		val outputPath = parent == null ? Paths.get(OUTPUT_DIR, LINKS_FILE_NAME) :
			Paths.get(OUTPUT_DIR, parent.getHost(), parent.getPath(), LINKS_FILE_NAME);
		try {
			Files.createDirectories(outputPath.getParent());
		} catch(final IOException e) {
			e.printStackTrace(System.err);
		}
		try(
			val linksFileWriter = new LinksFileWriter(outputPath)
		) {
			inStream
				.map(UrlUtil::convertToUrlWithoutAnchorAndQuery)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.filter(urlFilter)
				.map(url -> new HttpUrlProcessingTask(this, url))
				.peek(executor::submit)
				.map(HttpUrlProcessingTask::getUrl)
				.forEach(url -> linksFileWriter.writeURL(url.toString()));
		} catch(final IOException e) {
			e.printStackTrace(System.err);
		}
	}

	private static String outputDir() {
		String configured = System.getProperty("jobot.out");
		if(configured != null)
			return configured;
		return System.getProperty("user.home");
	}

	private static class LinksFileWriter implements Closeable {
		private final Lock lock = new ReentrantLock();
		private final BufferedWriter linksFileWriter;

		LinksFileWriter(Path outputPath) throws IOException {
			linksFileWriter = Files.newBufferedWriter(
				outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
			);
		}

		/** Serialises writing to file in case if it is done from different threads. */
		void writeURL(String link) {
			lock.lock();
			try {
				linksFileWriter.append(link);
				linksFileWriter.newLine();
			} catch(final IOException e) {
				e.printStackTrace(System.err);
			} finally {
				lock.unlock();
			}
		}

		@Override public void close() throws IOException {
			linksFileWriter.close();
		}
	}
}
