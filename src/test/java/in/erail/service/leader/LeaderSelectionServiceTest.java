package in.erail.service.leader;


import io.vertx.core.json.JsonObject;
import in.erail.glue.Glue;
import io.reactivex.Single;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.eventbus.EventBus;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 *
 * @author vinay
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 1, timeUnit = TimeUnit.MINUTES)
public class LeaderSelectionServiceTest {

  @Test
  public void testRegister(VertxTestContext testContext) {

    Checkpoint chkpoint = testContext.laxCheckpoint(3);

    LeaderSelectionService service = Glue.instance().resolve("/in/erail/service/leader/LeaderSelectionService");

    String session = "FAKE_SESSION";

    JsonObject regsiterMsg = new JsonObject();
    regsiterMsg.put("type", BridgeEventType.REGISTER.toString());
    regsiterMsg.put("address", "ninja-live");
    regsiterMsg.put("session", session);

    JsonObject unregsiterMsg = new JsonObject();
    unregsiterMsg.put("type", BridgeEventType.UNREGISTER.toString());
    unregsiterMsg.put("address", "ninja-live");
    unregsiterMsg.put("session", "FAKE_LEADER_SOCKET");

    EventBus eb = service.getVertx().eventBus();

    eb
            .<JsonObject>consumer("ninja-live")
            .toObservable()
            .firstOrError()
            .subscribe((msg) -> {
              //Step 2: Got Leader Selection Message
              String leaderId = msg.body().getString("leader");
              //This will be done by BridgeEventHandler
              DeliveryOptions delOpt = new DeliveryOptions();
              delOpt.addHeader("session", "FAKE_LEADER_SOCKET");

              //Step 3: Send Reply confirmation leadership
              eb
                      .rxSend(leaderId, new JsonObject(), delOpt)
                      .subscribe((reply) -> {
                        //Step 4: Got empty confirmation on becoming leader
                        chkpoint.flag();
                        service
                                .getVertx()
                                .sharedData()
                                .<String, String>rxGetClusterWideMap(service.getLeaderMapName())
                                .subscribe((m) -> {
                                  service.getVertx().setTimer(100, (p) -> {
                                    //Step 5: Check leader is correctly set in map
                                    m
                                            .rxGet("ninja-live")
                                            .subscribe((v) -> {
                                              Assertions.assertEquals("FAKE_LEADER_SOCKET", v);
                                              chkpoint.flag();
                                              
                                              //Step 6: Unregister leader
                                              service
                                                      .getVertx()
                                                      .eventBus()
                                                      .send(service.getBridgeEventUpdateTopicName(), unregsiterMsg);

                                              service.getVertx().setTimer(100, (p2) -> {
                                                //Step 7: Check leader has been unregistered
                                                m.rxGet("ninja-live").subscribe((v2) -> {
                                                  if (v2 == null || LeaderSelectionService.DEFAULT_LEADER_STATUS.equals(v2)) {
                                                    chkpoint.flag();
                                                    return;
                                                  }
                                                  Assertions.fail("Wrong map value");
                                                });
                                              });
                                            });
                                  });
                                });
                      });
            });
    
    Single.timer(100, TimeUnit.MILLISECONDS).subscribe((t) -> {
      //Step 1: Send register event
      service.getVertx().eventBus().send(service.getBridgeEventUpdateTopicName(), regsiterMsg);
    });

  }

}
