package models.daos.mongo

import com.google.inject.{Inject, Singleton}
import models.daos.CardDAO
import models.{Card, Picture}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{Json, OFormat}
import reactivemongo.play.json._

import scala.concurrent.Future

/**
  * Give access to the [[Card]] object.
  * Uses ReactiveMongo to access the MongoDB database
  */
@Singleton
class CardDAOMongo @Inject()(mongoDB: Mongo) extends CardDAO {

  implicit val cardJsonFormat: OFormat[Card] = Card.jsonFormat

  private[this] def cardColl = mongoDB.collection("card")

  override def find(fbID: String, cardID: String): Future[Option[Card]] =
    cardColl.flatMap(
      _.find(Json.obj("fbID" -> fbID, "cardID" -> cardID)).one[Card]
    )

  override def findAll(): Future[Seq[Card]] =
    cardColl.flatMap(
      _.find(Json.obj())
        .cursor[Card]()
        .collect[Seq](-1, Mongo.cursonErrorHandler[Card]("findAll in card dao"))
    )

  override def findAllCards(fbID: String): Future[Seq[Card]] =
    cardColl.flatMap(
      _.find(Json.obj("fbID" -> fbID))
        .cursor[Card]()
        .collect[Seq](-1, Mongo.cursonErrorHandler[Card]("findAllCards in card dao"))
    )

  override def findAllUsers(cardID: String): Future[Seq[Card]] =
    cardColl.flatMap(
      _.find(Json.obj("cardID" -> cardID))
        .cursor[Card]()
        .collect[Seq](-1, Mongo.cursonErrorHandler[Card]("findAllUsers in card dao"))
    )

  override def save(card: Card): Future[Card] =
    cardColl
      .flatMap(_.update(Json.obj("fbID" -> card.fbID, "cardID" -> card.cardID), card, upsert = true))
      .transform(
        _ => card,
        t => t
      )
  override def savePicture(fbID: String, cardID: String, picture: Picture): Future[Card] = {
    find(fbID, cardID)
      .flatMap{ cardAlreadyStored =>
        save(cardAlreadyStored.getOrElse(Card(cardID, fbID)).updatePicture(picture))
      }
  }

  override def remove(fbID: String, cardID: String): Future[Unit] =
    cardColl
      .flatMap(_.remove(Json.obj("fbID" -> fbID, "cardID" -> cardID)))
      .transform(
        _ => (),
        t => t
      )


  override def removePicture(fbID: String, cardID: String, fileName: String): Future[Option[Card]] = {
    find(fbID, cardID)
      .map{cardAlreadyStored =>
        if(cardAlreadyStored.isEmpty || !cardAlreadyStored.get.getNotDeleted.pictures.exists(_.fileName == fileName)){
          Future.successful(None)
          }
        /*else if (cardAlreadyStored.get.pictures.length == 1 && cardAlreadyStored.get.pictures.head.fileName == fileName) {
           remove(fbID, cardID).map(_ => None)
        } */
        else {
          val cardToStore = cardAlreadyStored.get.removePic(fileName)
          save(cardToStore).map(Some(_))
        }
      }.flatMap(cardSaved => cardSaved)
  }

}
