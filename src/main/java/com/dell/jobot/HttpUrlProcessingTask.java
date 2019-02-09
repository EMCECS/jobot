package com.dell.jobot;

import lombok.Value;
import lombok.val;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.stream.Stream;
import java.util.Optional;

import static com.dell.jobot.HyperlinkUtil.extractLinks;

@Value
public class HttpUrlProcessingTask
implements Runnable {

	private final HttpUrlStreamHandler handler;
	private final URL url;

	public HttpUrlProcessingTask(final HttpUrlStreamHandler handler, final URL url) {
		this.handler = handler;
		this.url = url;
	}

	@Override
	public void run() {
		connect(url).ifPresent(this::handleConnection);
	}

	static Optional<HttpURLConnection> connect(final URL url) {
		try {
			return Optional.of((HttpURLConnection) url.openConnection());
		} catch(final IOException e) {
			System.err.println("Was unable to connect the server \"" + url.getHost() + ":" + url.getPort() + "\"");
			return Optional.empty();
		}
	}

	void handleConnection(final HttpURLConnection conn) {
		sendRequest(conn);
		handleResponse(conn);
	}

	static void sendRequest(final HttpURLConnection conn) {
		try {
			conn.setRequestMethod("GET");
		} catch(final ProtocolException e) {
			e.printStackTrace(System.err);
		}
		conn.setRequestProperty("User-Agent", "jobot");
	}

	void handleResponse(final HttpURLConnection conn) {
		try {
			val respCode = conn.getResponseCode();
			if(respCode < 400) {
				if(respCode >= 300) {
					conn.setInstanceFollowRedirects(true);
				}
				val contentType = conn.getContentType();
				if(contentType.startsWith("text")) {
					handleResponseContent(conn);
				} else {
					System.out.println("Filtered by content type (" + contentType + "): " + url);
				}
			} else {
				System.err.println("Response code " + respCode +" for: " + url);
			}
		} catch(final IOException e) {
			System.err.println("I/O failure while reading the response from the url: \"" + url + "\"");
		}
	}

	void handleResponseContent(final HttpURLConnection conn) {
		System.out.println("Downloading " + url + " ...");
		try(val contentReader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
			String line;
			while(null != (line = contentReader.readLine())) {
				// IMPL NOTE we expect that repeating links will be dealt with in handler
				//   (see FixedCacheUniquenessFilter).
				extractLinks(line, (s) -> handler.handle(url, Stream.of(s)));
			}
		} catch(final IOException e) {
			System.err.println("I/O failure while reading the content from the url: \"" + url + "\"");
		} catch(final Throwable t) {
			t.printStackTrace(System.err);
		}
	}
}
