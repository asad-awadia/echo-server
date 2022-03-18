package dev.aawadia

import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.datagram.DatagramSocketOptions
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await

fun main() {
  val vertx = Vertx.vertx()

  vertx.deployVerticle(EchoServer::class.java, getDeploymentOptions())
    .onFailure { it.printStackTrace() }
    .onSuccess { println("deployed echo server..") }
}

class EchoServer : CoroutineVerticle() {
  override suspend fun start() {
    super.start()

    val tcpCounter = vertx.sharedData().getCounter("tcpPrint").await().incrementAndGet().await()
    val httpPrint = vertx.sharedData().getCounter("httpPrint").await().incrementAndGet().await()
    val wsPrint = vertx.sharedData().getCounter("wsPrint").await().incrementAndGet().await()
    val udpPrint = vertx.sharedData().getCounter("udpPrinted").await().incrementAndGet().await()

    vertx.createNetServer()
      .connectHandler { it.pipe().to(it) }
      .listen(config.getInteger("tcpPort", 9090))
      .onSuccess { if (tcpCounter == 1L) println("tcp server ready on port ${it.actualPort()}") }
      .onFailure { it.printStackTrace() }

    vertx.createHttpServer()
      .webSocketHandler { ws -> ws.handler { ws.writeTextMessage(it.toString()) } }
      .listen(config.getInteger("wsPort", 9092))
      .onSuccess { if (httpPrint == 1L) println("http server ready on port ${it.actualPort()}") }
      .onFailure { it.printStackTrace() }

    vertx.createHttpServer()
      .requestHandler {
        it.response().isChunked = true
        it.response().headers().setAll(it.headers())
        it.pipe().to(it.response())
      }.listen(config.getInteger("httpPort", 9091))
      .onSuccess { if (wsPrint == 1L) println("ws server ready on port ${it.actualPort()}") }
      .onFailure { it.printStackTrace() }

    val datagramSocket = vertx.createDatagramSocket(DatagramSocketOptions().setReuseAddress(true).setReusePort(true))
      .listen(config.getInteger("udpPort", 9093), "0.0.0.0")
      .onSuccess { if (udpPrint == 1L) println("udp server ready on port ${it.localAddress().port()}") }
      .await()
    datagramSocket.handler {
      datagramSocket.send(
        it.data(),
        config.getInteger("udpReplyPort", 9094),
        it.sender().host()
      )
    }
  }
}

fun getDeploymentOptions(
  tcpPort: Int = 9090,
  httpPort: Int = 9091,
  wsPort: Int = 9092,
  udpPort: Int = 9093,
  instances: Int = 2 * Runtime.getRuntime().availableProcessors()
): DeploymentOptions {
  return DeploymentOptions()
    .setInstances(instances)
    .setWorkerPoolSize(16 * instances)
    .setConfig(
      JsonObject()
        .put("tcpPort", System.getenv("tcp_port")?.toIntOrNull() ?: tcpPort)
        .put("httpPort", System.getenv("http_host")?.toIntOrNull() ?: httpPort)
        .put("wsPort", System.getenv("ws_port")?.toIntOrNull() ?: wsPort)
        .put("udpPort", System.getenv("udp_port")?.toIntOrNull() ?: udpPort)
        .put("udpReplyPort", System.getenv("udp_reply_port")?.toIntOrNull() ?: 9094)
    )
}
