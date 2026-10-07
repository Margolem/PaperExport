package dev.paperexport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PackServerTest {
    @TempDir Path folder;
    @Test void servesAnImmutableSnapshotAndRejectsOtherPathsAndMethods() throws Exception {
        Path file=folder.resolve("resourcepack.zip");Files.write(file,new byte[]{1,2,3});
        try(var server=new PaperPackServer("127.0.0.1",0);var client=HttpClient.newHttpClient()) {
            server.update(new PaperResourcePackBuilder.Result(file,"abc",1,java.util.Map.of()));
            Files.write(file,new byte[]{9});
            String base="http://127.0.0.1:"+server.port();
            var request=HttpRequest.newBuilder(URI.create(base+server.path())).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200,response.statusCode());assertArrayEquals(new byte[]{1,2,3},response.body());
            var head=client.send(HttpRequest.newBuilder(request.uri()).method("HEAD",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200,head.statusCode());assertEquals(0,head.body().length);assertEquals("3",head.headers().firstValue("content-length").orElseThrow());
            assertEquals(404,client.send(HttpRequest.newBuilder(URI.create(base+"/config.yml")).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(405,client.send(HttpRequest.newBuilder(request.uri()).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            server.update(new PaperResourcePackBuilder.Result(file,"def",1,java.util.Map.of()));
            assertEquals(404,client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode());
        }
    }
}
