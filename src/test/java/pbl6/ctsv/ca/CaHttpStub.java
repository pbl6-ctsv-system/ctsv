package pbl6.ctsv.ca;

import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import pbl6.ctsv.shared.ca.*;

final class CaHttpStub implements AutoCloseable {
    static final String ACCESS = CaTokenTestSupport.access(java.time.Instant.now().plusSeconds(3600), 1);
    static final String ACCESS_2 = CaTokenTestSupport.access(java.time.Instant.now().plusSeconds(3600), 2);
    record Request(String method,String path,String query,String authorization,byte[] body) {}
    record Reply(int status,String contentType,byte[] body) {
        static Reply json(int status,String body) {return new Reply(status,"application/json",body.getBytes(StandardCharsets.UTF_8));}
    }
    final HttpServer server;
    final ExecutorService threads=Executors.newCachedThreadPool();
    final List<Request> requests=new CopyOnWriteArrayList<>();
    volatile Function<Request,Reply> handler = r -> Reply.json(404,"{}");
    CaHttpStub() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(threads);
        server.createContext("/",e->{
            var r=new Request(e.getRequestMethod(),e.getRequestURI().getPath(),e.getRequestURI().getRawQuery(),e.getRequestHeaders().getFirst("Authorization"),e.getRequestBody().readAllBytes());requests.add(r);
            var reply=handler.apply(r);e.getResponseHeaders().set("Content-Type",reply.contentType());
            e.sendResponseHeaders(reply.status(),reply.body().length);try(var out=e.getResponseBody()){out.write(reply.body());}catch(java.io.IOException ignored){} e.close();
        });server.start();
    }
    URI origin(){return URI.create("http://127.0.0.1:"+server.getAddress().getPort());}
    CaDutClient client(){return CaDutClient.forLoopbackStub(origin(),"synthetic-client","synthetic-secret",java.time.Clock.systemUTC(),3000);}
    static Reply token(){return Reply.json(200,"{\"success\":true,\"data\":{\"access\":\""+ACCESS+"\",\"refresh\":\"refresh-1\"}}");}
    public void close(){server.stop(0);threads.shutdownNow();}
}
