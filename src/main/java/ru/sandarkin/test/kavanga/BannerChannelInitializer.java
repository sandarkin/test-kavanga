package ru.sandarkin.test.kavanga;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.sql.Connection;

public class BannerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private Connection conn;

    public BannerChannelInitializer(Connection conn) {
        this.conn = conn;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast(new HttpRequestDecoder());
        pipeline.addLast(new HttpResponseEncoder());

        pipeline.addLast(new HttpObjectAggregator(65536));
        pipeline.addLast(new ChunkedWriteHandler());
        pipeline.addLast(new BannerDownloadChannelHandler(conn));

        pipeline.addLast(new HttpContentCompressor());
        pipeline.addLast(new BannerUploadChannelHandler());
        pipeline.addLast(new BannerTopChannelHandler(conn));
    }

}
