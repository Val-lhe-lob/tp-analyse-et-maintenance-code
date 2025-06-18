package com.simplecity.amp_library.http;

import android.util.Log;
import fi.iki.elonen.NanoHTTPD;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HttpServer {

    private static final String TAG = "HttpServer";
    private static final String MIME_TYPE_HTML = "text/html";

    private final NanoServer server;
    private String audioFileToServe;
    private byte[] imageBytesToServe;
    private FileInputStream audioInputStream;
    private ByteArrayInputStream imageInputStream;
    private boolean isStarted = false;

    private final Map<String, String> mimeTypes = createMimeTypes();

    private HttpServer() {
        server = new NanoServer();
    }

    public static HttpServer getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final HttpServer INSTANCE = new HttpServer();
    }

    public static HttpServer getInstance() {
        if (sHttpServer == null) {
            sHttpServer = new HttpServer();
        }
        return sHttpServer;
    }

    private HttpServer() {
        server = new NanoServer();
    }

    public void serveAudio(String audioUri) {
        if (audioUri != null) {
            audioFileToServe = audioUri;
        }
    }

    public void serveImage(byte[] imageBytes) {
        if (imageBytes != null) {
            imageBytesToServe = imageBytes;
        }
    }

    public void clearImage() {
        imageBytesToServe = null;
    }

    public void start() {
        if (!isStarted) {
            try {
                server.start();
                isStarted = true;
            } catch (IOException e) {
                Log.e(TAG, "Error starting server: " + e.getMessage());
            }
        }
    }

    public void stop() {
        if (isStarted) {
            server.stop();
            isStarted = false;
            cleanupAudioStream();
            cleanupImageStream();
        }
    }

    private class NanoServer extends NanoHTTPD {

        NanoServer() {
            super(5000);
        }

        @Override
        public Response serve(IHTTPSession session) {

            if (audioFileToServe == null) {
                Log.e(TAG, "Audio file to serve null");
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_TYPE_HTML, "File not found");
            }

            String uri = session.getUri();
            if (uri.contains("audio")) {
                try {
                    File file = new File(audioFileToServe);

                    Map<String, String> headers = session.getHeaders();
                    String range = null;
                    for (String key : headers.entrySet()) {
                        if ("range".equals(key)) {
                            range = headers.get(key);
                        }
                    }

                    if (range == null) {
                        range = "bytes=0-";
                        session.getHeaders().put("range", range);
                    }

                    long start;
                    long end;
                    long fileLength = file.length();

                    String rangeValue = range.trim().substring("bytes=".length());

                    if (rangeValue.startsWith("-")) {
                        end = fileLength - 1;
                        start = fileLength - 1 - Long.parseLong(rangeValue.substring(1));
                    } else {
                        String[] ranges = rangeValue.split("-");
                        start = Long.parseLong(ranges[0]);
                        end = (ranges.length > 1) ? Long.parseLong(ranges[1]) : fileLength - 1;
                    }

                    if (end > fileLength - 1) {
                        end = fileLength - 1;
                    }

                    if (start <= end) {
                        long contentLength = end - start + 1;
                        cleanupAudioStream();
                        audioInputStream = new FileInputStream(file);
                        long skipped = audioInputStream.skip(start);
                        if (skipped < start) {
                            Log.w(TAG, "Skipped only " + skipped + " of " + start + " bytes");
                            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_TYPE_HTML, "Unable to skip to requested byte range");
                        }
                        Response response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, getMimeType(audioFileToServe), audioInputStream, contentLength);
                        response.addHeader("Content-Length", String.valueOf(contentLength));
                        response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                        response.addHeader("Content-Type", getMimeType(audioFileToServe));
                        return response;
                    } else {
                        return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_TYPE_HTML, range);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error serving audio: " + e.getMessage());
                    e.printStackTrace();
                }
            } else if (uri.contains("image")) {
                if (imageBytesToServe == null) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_TYPE_HTML, "Image bytes null");
                }
                cleanupImageStream();
                imageInputStream = new ByteArrayInputStream(imageBytesToServe);
                Log.i(TAG, "Serving image bytes: " + imageBytesToServe.length);
                return newFixedLengthResponse(Response.Status.OK, "image/png", imageInputStream, imageBytesToServe.length);
            }

            Log.e(TAG, "Returning NOT_FOUND response");
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_TYPE_HTML, "File not found");
        }
    }

    void cleanupAudioStream() {
        if (audioInputStream != null) {
            try {
                audioInputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    void cleanupImageStream() {
        if (imageInputStream != null) {
            try {
                imageInputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    private final Map<String, String> mimeTypes = createMimeTypes();

    private static Map<String, String> createMimeTypes() {
        Map<String, String> map = new HashMap<>();
        map.put("css", "text/css");
        map.put("htm", "text/html");
        map.put("html", "text/html");
        map.put("xml", "text/xml");
        map.put("java", "text/x-java-source, text/java");
        map.put("md", "text/plain");
        map.put("txt", "text/plain");
        map.put("asc", "text/plain");
        map.put("gif", "image/gif");
        map.put("jpg", "image/jpeg");
        map.put("jpeg", "image/jpeg");
        map.put("png", "image/png");
        map.put("mp3", "audio/mpeg");
        map.put("m3u", "audio/mpeg-url");
        map.put("mp4", "video/mp4");
        map.put("ogv", "video/ogg");
        map.put("flv", "video/x-flv");
        map.put("mov", "video/quicktime");
        map.put("swf", "application/x-shockwave-flash");
        map.put("js", "application/javascript");
        map.put("pdf", "application/pdf");
        map.put("doc", "application/msword");
        map.put("ogg", "application/x-ogg");
        map.put("zip", "application/octet-stream");
        map.put("exe", "application/octet-stream");
        map.put("class", "application/octet-stream");
        return map;
    }

    String getMimeType(String filePath) {
        return mimeTypes.get(filePath.substring(filePath.lastIndexOf(".") + 1));
    }
}
