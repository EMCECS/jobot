package com.dell.jobot;

import lombok.val;

import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static com.dell.jobot.UrlUtil.HTTP_FILTER;
import static java.util.concurrent.TimeUnit.SECONDS;

@SuppressWarnings("CommentAbsent")
public class Main {

	private static final int QUEUE_CAPACITY = 10; // todo recover larger value after smoke testing: 1_000_000;
	private static final int CACHE_CAPACITY = 10; // todo recover larger value after smoke testing: 1_000_000;

	public static void main(final String... args) {
		if(0 == args.length) {
			printUsage();
			return;
		}
		ThreadPoolExecutor executor = setupExecutor();
		HttpUrlStreamHandler handler = setupHandler(executor);
		try {
			handler.handle(null, Arrays.stream(args));
			executor.awaitTermination(/*todo recover larger value after smoke testing: Long.MAX_VALUE*/ 10, SECONDS);
		} catch(final Exception e) {
			e.printStackTrace(System.err);
		}
	}

	private static HttpUrlStreamHandler setupHandler(ThreadPoolExecutor executor) {
		val uniqueUrlFilter = new FixedCacheUniquenessFilter<URL>(CACHE_CAPACITY);
		return new HttpUrlStreamHandler(executor, url -> HTTP_FILTER.test(url) && uniqueUrlFilter.test(url));
	}

	private static ThreadPoolExecutor setupExecutor() {
		val parallelism = Runtime.getRuntime().availableProcessors();
		val queue = new ArrayBlockingQueue<Runnable>(QUEUE_CAPACITY);
		val threadFactory = new DaemonThreadFactory();
		val rejectionHandler = new IgnoringRejectionExecutionHandler();
		return new ThreadPoolExecutor(
				parallelism, parallelism, 0, SECONDS, queue, threadFactory, rejectionHandler
		);
	}

	private static void printUsage() {
		System.out.println("Useless internet crawler command line options: url1 [url2 [url3 ...]]");
	}
}
