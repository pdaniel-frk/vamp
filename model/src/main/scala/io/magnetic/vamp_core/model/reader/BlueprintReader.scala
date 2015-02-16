package io.magnetic.vamp_core.model.reader

import io.magnetic.vamp_core.model._

import scala.language.postfixOps

object BlueprintReader extends YamlReader[Blueprint] {

  override protected def expand(implicit source: YamlObject) = {
    <<?[YamlObject]("clusters") match {
      case None =>
      case Some(map) => map.map {
        case (name: String, cluster: collection.Map[_, _]) =>
          implicit val source = cluster.asInstanceOf[YamlObject]
          <<?[List[YamlObject]]("services").map {
            _.foreach { service =>
              implicit val source = service
              <<?[Any]("routing" :: "filters").map {
                _ => expandToList("routing" :: "filters")
              }
            }
          }
      }
    }

    super.expand
  }

  override def parse(implicit source: YamlObject): Blueprint = {

    val clusters = <<?[YamlObject]("clusters") match {
      case None => List[Cluster]()
      case Some(map) => map.map({
        case (name: String, cluster: collection.Map[_, _]) =>
          implicit val source = cluster.asInstanceOf[YamlObject]
          val sla = SlaReader.readOptionalReference("sla")

          <<?[List[YamlObject]]("services") match {
            case None => Cluster(name, List(), sla)
            case Some(list) => Cluster(name, list.map(service(_)), sla)
          }
      }).toList
    }

    Blueprint(name, clusters, stringMap("endpoints"), stringMap("parameters"))
  }

  override protected def validate(blueprint: Blueprint): Blueprint = blueprint // validate endpoints, parameters (cluster references)

  private def service(implicit source: YamlObject): Service =
    Service(BreedReader.readReference(<<![YamlObject]("breed")), ScaleReader.readOptionalReference("scale"), RoutingReader.readOptionalReference("routing"))
}

object SlaReader extends YamlReader[Sla] with WeakReferenceYamlReader[Sla] {

  override protected def validate(implicit source: YamlObject): YamlObject = {
    if (source.filterKeys(k => k != "name" && k != "escalations").nonEmpty) super.validate
    source
  }

  override protected def createReference(implicit source: YamlObject): Sla = SlaReference(reference, escalations)

  override protected def createAnonymous(implicit source: YamlObject): Sla = AnonymousSla(`type`, escalations, parameters)

  protected def escalations(implicit source: YamlObject): List[Escalation] = <<?[YamlList]("escalations") match {
    case None => List[Escalation]()
    case Some(list: YamlList) => list.map {
      EscalationReader.readReference
    }
  }

  override protected def parameters(implicit source: YamlObject): Map[String, Any] = super.parameters.filterKeys(_ != "escalations")
}

object EscalationReader extends YamlReader[Escalation] with WeakReferenceYamlReader[Escalation] {

  override protected def createReference(implicit source: YamlObject): Escalation = EscalationReference(reference)

  override protected def createAnonymous(implicit source: YamlObject): Escalation = AnonymousEscalation(`type`, parameters)
}

object ScaleReader extends YamlReader[Scale] with WeakReferenceYamlReader[Scale] {

  override protected def createReference(implicit source: YamlObject): Scale = ScaleReference(reference)

  override protected def createAnonymous(implicit source: YamlObject): Scale = AnonymousScale(<<![Double]("cpu"), <<![Double]("memory"), <<![Int]("instances"))
}

object RoutingReader extends YamlReader[Routing] with WeakReferenceYamlReader[Routing] {

  override protected def createReference(implicit source: YamlObject): Routing = RoutingReference(reference)

  override protected def createAnonymous(implicit source: YamlObject): Routing = AnonymousRouting(<<?[Int]("weight"), filters)

  protected def filters(implicit source: YamlObject): List[Filter] = <<?[YamlList]("filters") match {
    case None => List[Filter]()
    case Some(list: YamlList) => list.map {
      FilterReader.readReference
    }
  }
}

object FilterReader extends YamlReader[Filter] with WeakReferenceYamlReader[Filter] {

  override protected def createReference(implicit source: YamlObject): Filter = FilterReference(reference)

  override protected def createAnonymous(implicit source: YamlObject): Filter = AnonymousFilter(<<![String]("condition"))
}
