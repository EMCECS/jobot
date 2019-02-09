package com.dell.jobot;

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

@SuppressWarnings({"CommentAbsent", "GridAnnotation"})
public class HttpUrlStreamHandler
implements RawUrlStreamHandler {

	private static final String OUTPUT_DIR = outputDir() + File.separator + ".jobot";
	private static final String LINKS_FILE_NAME = "links.txt";

	private final ExecutorService executor;
	private final Predicate<URL> urlFilter;

	HttpUrlStreamHandler(final ExecutorService executor, final Predicate<URL> urlFilter) {
		this.executor = executor;
		this.urlFilter = urlFilter;
	}

	@Override
	public void handle(final URL parent, final @NonNull Stream<String> inStream) {
		val outputPath = parent == null ?
			Paths.get(OUTPUT_DIR, LINKS_FILE_NAME) :
			Paths.get(OUTPUT_DIR, parent.getHost(), parent.getPath(), LINKS_FILE_NAME);
		try {
			Files.createDirectories(outputPath.getParent());
		} catch(final IOException e) {
			e.printStackTrace(System.err);
		}
		try(
			val linksFileWriter = Files.newBufferedWriter(
				outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
			)
		) {
			inStream
				.map(UrlUtil::convertToUrlWithoutAnchorAndQuery)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.filter(urlFilter)
				.map(url -> new HttpUrlProcessingTask(this, url))
				.peek(executor::submit)
				.map(HttpUrlProcessingTask::getUrl)
				.forEach(
					url -> {
						try {
							linksFileWriter.append(url.toString());
							linksFileWriter.newLine();
						} catch(final IOException e) {
							e.printStackTrace(System.err);
						}
					}
				);
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
}
