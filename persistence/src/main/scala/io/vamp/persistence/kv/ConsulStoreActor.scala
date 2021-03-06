package io.vamp.persistence.kv

import java.util.Base64

import akka.http.scaladsl.model.ContentTypes
import io.vamp.common.config.Config

import scala.concurrent.Future

class ConsulStoreActor extends KeyValueStoreActor {

  private val url = Config.string("vamp.persistence.key-value-store.consul.url")

  override protected def info(): Future[Any] = restClient.get[Any](s"$url/v1/agent/self") map { consul ⇒
    Map("type" -> "consul", "consul" -> consul)
  }

  override protected def all(path: List[String]): Future[List[String]] = {
    val key = KeyValueStoreActor.pathToString(path)
    checked[List[String]](restClient.get[List[String]](urlOf(path, keys = true), logError = false) recover { case _ ⇒ Nil }) map { list ⇒
      list.map(_.substring(key.length))
    }
  }

  override protected def get(path: List[String]): Future[Option[String]] = {
    restClient.get[List[_]](urlOf(path), logError = false) recover { case _ ⇒ None } map {
      case head :: Nil ⇒ Option(result(head.asInstanceOf[Map[_, _]]))
      case _           ⇒ None
    }
  }

  override protected def set(path: List[String], data: Option[String]): Future[Any] = data match {
    case None        ⇒ restClient.delete(urlOf(path), logError = false)
    case Some(value) ⇒ restClient.put[Any](urlOf(path), value, contentType = ContentTypes.`text/plain(UTF-8)`)
  }

  private def urlOf(path: List[String], keys: Boolean = false) = {
    s"$url/v1/kv${KeyValueStoreActor.pathToString(path)}${if (keys) "?keys" else ""}"
  }

  private def result(map: Map[_, _]): String = {
    map.asInstanceOf[Map[String, _]].get("Value").map(value ⇒ Base64.getDecoder.decode(value.asInstanceOf[String])).map(new String(_)).getOrElse("")
  }
}
