package kamon.stackdriver

import kamon.stackdriver.StackdriverAuditMarker._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json._

class StackdriverAuditMarkerTest extends AnyWordSpec with Matchers {

  private val audit =
    Audit(
      Caller(gwiOrgId = 1, gwiUserId = 2),
      Action(`type` = ActionType.Create, message = "foo-message"),
      Target(`type` = "target-type", 3, "target-name")
    )
  private def builder = JsonStringBuilder.getSingleThreaded

  "StackdriverAuditMarker" should {
    "encode audit to json" when {
      "given valid audit data" in {
        val auditJson = StackdriverAuditMarker(audit).encode(builder).result
        auditJson.stripPrefix(""""audit":""").parseJson.asJsObject
        auditJson shouldBe
          """"audit":{"caller":{"gwi_org_id":1,"gwi_user_id":2},"action":{"type":"Create","message":"foo-message"},"target":{"type":"target-type","name":"target-name","id":3}}"""

      }
    }
  }
}
