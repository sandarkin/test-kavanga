package ru.sandarkin.test.kavanga;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.internal.SystemPropertyUtil;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Server that accept the id of a banner an echo back its content.
 */
public final class BannerServer {

    static final int PORT = 8888;

    public static void main(String[] args) throws Exception {

        Class.forName("org.h2.Driver");

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try (Connection conn = DriverManager.getConnection("jdbc:h2:" + SystemPropertyUtil.get("user.dir")
                + File.separator + "assets" + File.separator + "kavanga")) {

            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new BannerChannelInitializer(conn));
            ChannelFuture f = b.bind(PORT).sync();
            f.channel().closeFuture().sync();

        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();

        }
    }

}
