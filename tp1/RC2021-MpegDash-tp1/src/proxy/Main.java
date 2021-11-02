package proxy;

import java.util.concurrent.BlockingQueue;

import http.HttpClient;
import http.HttpClient10;
import media.MovieManifest;
import media.MovieManifest.Manifest;
import media.MovieManifest.SegmentContent;
import proxy.server.ProxyServer;

public class Main {
	static final String MEDIA_SERVER_BASE_URL = "http://localhost:80";

	public static void main(String[] args) throws Exception {

		ProxyServer.start( (movie, queue) -> new DashPlaybackHandler(movie, queue) );
		
	}
	/**
	 * TODO TODO TODO TODO
	 * 
	 * Class that implements the client-side logic.
	 * 
	 * Feeds the player queue with movie segment data fetched
	 * from the HTTP server.
	 * 
	 * The fetch algorithm should prioritize:
	 * 1) avoid stalling the browser player by allowing the queue to go empty
	 * 2) if network conditions allow, retrieve segments from higher quality tracks
	 */
	static class DashPlaybackHandler implements Runnable  {
		
		final String movie;
		final Manifest manifest;
		final BlockingQueue<SegmentContent> queue;

		final HttpClient http;
		
		DashPlaybackHandler( String movie, BlockingQueue<SegmentContent> queue) {
			this.movie = movie;
			this.queue = queue;
			
			this.http = new HttpClient10();
			
			var manifestURL = String.format("%s/%s/manifest.txt", MEDIA_SERVER_BASE_URL, movie);
			this.manifest = MovieManifest.parse(new String( http.doGet( manifestURL )));
		}
		
		/**
		 * Runs automatically in a dedicated thread...
		 * 
		 * Needs to feed the queue with segment data fast enough to
		 * avoid stalling the browser player
		 * 
		 * Upon reaching the end of stream, the queue should
		 * be fed with a zero-length data segment
		 */
		public void run() {
			try {
				var track = manifest.tracks().get(0);
				
				track.segments().forEach( System.err::println );
				
				for( var segment : track.segments() ) {
					System.err.println( "--->"  + segment);
					var segmentUrl = String.format("%s/%s/%s", MEDIA_SERVER_BASE_URL, movie, track.filename());
					var data = http.doGetRange(segmentUrl, segment.offset(), segment.offset() + segment.length() - 1 );
					System.err.printf("Fetched offset: %s, length: %s, data.length: %s ", segment.offset(), segment.length(), data.length);
					queue.put( new SegmentContent(track.contentType(), data));
				}
				queue.put( new SegmentContent(track.contentType(), new byte[0]));				
			} catch(Exception x ) {
				x.printStackTrace();
			}
		}
	}
}