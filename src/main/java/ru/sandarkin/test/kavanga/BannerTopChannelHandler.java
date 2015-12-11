package ru.sandarkin.test.kavanga;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpMethod.GET;

public class BannerTopChannelHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private Connection conn;

    private final StringBuilder responseContent = new StringBuilder();

    public BannerTopChannelHandler(Connection conn) {
        super();
        this.conn = conn;
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return FullHttpRequest.class.isAssignableFrom(msg.getClass())
                && (((FullHttpRequest) msg).getUri().equals("/top")
                || ((FullHttpRequest) msg).getMethod() != GET);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        writeTop(ctx);
    }

    private void writeTop(ChannelHandlerContext ctx) throws SQLException {
        responseContent.setLength(0);

        ClassLoader classLoader = getClass().getClassLoader();
        File indexFile = new File(classLoader.getResource("top.html").getFile());

        try (Scanner scanner = new Scanner(indexFile)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.contains("{top")) {
                    line = drawTopTable(line);
                }
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

    private String drawTopTable(String line) throws SQLException {
        StringBuilder sb = new StringBuilder("<table class=\"table table-hover\">");
        sb.append("<thead> <tr> <th>ID</th> <th>Показов</th> <th>Последний показ</th> </tr> </thead> <tbody>");
        String topCode = line.replaceFirst(".*\\{(.+)\\}.*", "$1");
        String query;
        if (topCode.endsWith("m")) {
            query = "SELECT id, count(id) AS qty, max(ts) AS lastts " +
                    " FROM ACCESS_LOG WHERE TIMESTAMPDIFF('MINUTE', ts, CURRENT_TIMESTAMP()) < 1" +
                    " GROUP BY id ORDER BY count(id) DESC LIMIT 100";
        } else {
            int hourQty = Integer.parseInt(topCode.replaceFirst("top(\\d)", "$1"));
            query = "SELECT id, count(id) AS qty, max(ts) AS lastts " +
                    " FROM ACCESS_LOG WHERE TIMESTAMPDIFF('HOUR', ts, CURRENT_TIMESTAMP()) < " + hourQty +
                    " GROUP BY id ORDER BY count(id) DESC LIMIT 100";
        }
        ResultSet rs;
        Statement stat = conn.createStatement();
        rs = stat.executeQuery(query);
        while (rs.next()) {
            sb.append("<tr><td>").append(rs.getInt("id")).append("</td>");
            sb.append("<td>").append(rs.getInt("qty")).append("</td>");
            sb.append("<td>").append(rs.getTimestamp("lastts")).append("</td></tr>");
        }
        stat.close();
        sb.append("<tbody></table>");
        return sb.toString();
    }


}
