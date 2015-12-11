package ru.sandarkin.test.kavanga;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.IncompatibleDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.SystemPropertyUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;

public class BannerUploadChannelHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = Logger.getLogger(BannerUploadChannelHandler.class.getName());

    private HttpRequest request;

    private final StringBuilder responseContent = new StringBuilder();

    private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);

    private HttpPostRequestDecoder decoder;

    static {
        DiskFileUpload.deleteOnExitTemporaryFile = true;
        DiskFileUpload.baseDirectory = null;
        DiskAttribute.deleteOnExitTemporaryFile = true;
        DiskAttribute.baseDirectory = null;
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        if (decoder != null) {
            decoder.cleanFiles();
        }
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return HttpRequest.class.isAssignableFrom(msg.getClass())
                && (((HttpRequest) msg).getUri().equals("/upload")
                || ((HttpRequest) msg).getUri().startsWith("/form"));
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = this.request = (HttpRequest) msg;
            URI uri = new URI(request.getUri());
            if (uri.getPath().equals("/upload")) {
                writeIndex(ctx);
            } else if (uri.getPath().startsWith("/form")) {
                responseContent.setLength(0);
                try {
                    decoder = new HttpPostRequestDecoder(factory, request);
                } catch (ErrorDataDecoderException | IncompatibleDataDecoderException e1) {
                    writeException(ctx, e1);
                    return;
                }


                if (msg instanceof HttpContent) {
                    HttpContent chunk = (HttpContent) msg;
                    try {
                        decoder.offer(chunk);
                    } catch (ErrorDataDecoderException e1) {
                        writeException(ctx, e1);
                        return;
                    }
                    readHttpDataChunkByChunk();
                    if (chunk instanceof LastHttpContent) {
                        writeResponse(ctx.channel());
                        reset();
                    }
                }

            }
        }
    }

    private void writeException(ChannelHandlerContext ctx, Exception ex) {
        ex.printStackTrace();
        responseContent.append(ex.getMessage());
        writeResponse(ctx.channel());
        ctx.channel().close();
    }

    private void reset() {
        request = null;
        decoder.destroy();
        decoder = null;
    }

    private void readHttpDataChunkByChunk() {
        try {
            while (decoder.hasNext()) {
                InterfaceHttpData data = decoder.next();
                if (data != null) {
                    try {
                        writeIncomingFiles(data);
                    } finally {
                        data.release();
                    }
                }
            }
        } catch (EndOfDataDecoderException e1) {
            // NO OP
        }
    }

    private void writeIncomingFiles(InterfaceHttpData data) {
        if (data.getHttpDataType() == HttpDataType.FileUpload) {
            FileUpload fileUpload = (FileUpload) data;
            if (fileUpload.isCompleted()) {
                if (fileUpload.length() < 65536) {
                    try {
                        String destPath = SystemPropertyUtil.get("user.dir") + File.separator + "assets" + File.separator + "banner"  + File.separator + fileUpload.getFilename();
                        fileUpload.renameTo(new File(destPath));
                        ClassLoader classLoader = getClass().getClassLoader();
                        File indexFile = new File(classLoader.getResource("result.html").getFile());

                        try (Scanner scanner = new Scanner(indexFile)) {
                            while (scanner.hasNextLine()) {
                                String line = scanner.nextLine();
                                if (line.contains("{fid}")) {
                                    line = line.replace("{fid}", fileUpload.getFilename().replaceFirst("(\\d+)\\..*+", "$1"));
                                    line = line.replace("{fname}", fileUpload.getFilename());
                                }
                                responseContent.append(line).append("\n");
                            }
                            scanner.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    Exception e1 = new IOException("\tFile too long to be printed out:" + fileUpload.length() + "\r\n");
                    responseContent.append(e1.getMessage());
                }
            } else {
                responseContent.append("\tFile to be continued but should not!\r\n");
            }
        }
    }

    private void writeResponse(Channel channel) {

        ByteBuf buf = copiedBuffer(responseContent.toString(), CharsetUtil.UTF_8);
        responseContent.setLength(0);

        boolean close = HttpHeaders.Values.CLOSE.equalsIgnoreCase(request.headers().get(CONNECTION))
                || request.getProtocolVersion().equals(HttpVersion.HTTP_1_0)
                && !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(request.headers().get(CONNECTION));

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        response.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");

        if (!close) {
            response.headers().set(CONTENT_LENGTH, buf.readableBytes());
        }

        ChannelFuture future = channel.writeAndFlush(response);
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void writeIndex(ChannelHandlerContext ctx) throws IOException {

        responseContent.setLength(0);

        ClassLoader classLoader = getClass().getClassLoader();
        File indexFile = new File(classLoader.getResource("index.html").getFile());

        try (Scanner scanner = new Scanner(indexFile)) {

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                responseContent.append(line).append("\n");
            }
            scanner.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ByteBuf buf = copiedBuffer(responseContent.toString(), CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);

        response.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
        response.headers().set(CONTENT_LENGTH, buf.readableBytes());

        ctx.channel().writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.log(Level.WARNING, responseContent.toString(), cause);
        ctx.channel().close();
    }

}
