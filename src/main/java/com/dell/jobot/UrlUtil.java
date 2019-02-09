package com.dell.jobot;

import java.util.function.BiFunction;
import lombok.NonNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.function.Predicate;

@SuppressWarnings("ALL") public interface UrlUtil {

	static Optional<URL> convertToUrlWithoutAnchorAndQuery(final @NonNull String raw) {
		BiFunction<String, Integer, String> trimAtIndex = (s, idx) -> idx < 0 ? s : s.substring(0, idx);

		String t = trimAtIndex.apply(raw, raw.indexOf('#'));

		t = trimAtIndex.apply(t, t.indexOf('?'));

		try {
			return Optional.of(new URL(t));
		} catch(final MalformedURLException e) {
			System.err.println("Failed to convert \"" + raw + "\" to URL");
			return Optional.empty();
		} catch(final Exception e) {
			throw new AssertionError("Unexpected failure while converting \"" + raw + "\" to URL", e);
		}
	}

	Predicate<URL> HTTP_FILTER = url -> url.getProtocol().startsWith("http");
}
