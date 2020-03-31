/*
THIS IS THE FOURTH EXAMPLE OF HOW TO USE VERT.X WEB. YOU CAN TEST THE ROUTES VIA CURL OR SIMILAR
here we get rid of the blocking nature of a JPA backend and make vert.x not blame us.

size: 26 MB
boot: 10 sec
memory after 2 queries: 395 MB
*/

package it.nunziatimatteo.skel

import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.Promise
import io.vertx.core.json.Json
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.ResponseContentTypeHandler
import javax.persistence.*


class MainVerticle : AbstractVerticle() {

  override fun start(startPromise: Promise<Void>) {
    val theServer = vertx.createHttpServer()
    val mainRouter = Router.router(vertx)

    mainRouter.route().handler(ResponseContentTypeHandler.create()) //auto response content-type in headers for all routes
    mainRouter.route().handler(BodyHandler.create()) //request body extractor and formatter for all routes
    mainRouter.route().failureHandler { frc -> this.manageFailure(frc) } //failure manager for all routes

    /* NEW NEW NEW */
    //here we use the 'executeBlocking' promise to let blocking code run in background and return an async promise
    //and everything else depending on it is executed into the response of the promise.
    //an executeBlocking basically creates a promise, we catch the promise and we execute our blocking stuff asynchronously.
    //when we have finished our computations we grab the promise we have catch and we push into it the result of our computations
    //
    //"on the other side" there is the main loop suspended (NOT BLOCKED) and waiting for us. the promise response catches the result we have push into the promise
    //and let us manipulate it when it is ready.
    //
    //basically we suspend the entire main thread until hibernate - in the background - has build its factory. The we grab the factory and we use it.

    vertx.executeBlocking({ promise: Promise<Any> ->

      val emf = Persistence.createEntityManagerFactory( "org.hibernate.tutorial.jpa" ) //init JPA from persistence.xml
      promise.complete(emf) //put it into a promise

    }) {
      //this is the response of the promise and catches the emf value when it is init'ed. we use it a lambda.
      //when a promise returns something, this something is wrapped into a generic object...
      promiseExecution ->

        //... here we use the really expressive kotlin way to cast values and extract the original emf from the wrapper
        val emf = promiseExecution.result() as EntityManagerFactory

        //this time we mock up a request info about a book's author by knowing her id. the response is a JSON.
        mainRouter.get("/authorById/:id").produces("text/json").handler { ctx -> getAuthorById(ctx, emf) }

        //this time we are asked to DELETE an author by id, this is useful to mock an error of a request
        mainRouter.delete("/deleteAuthorByIdFails/:id").handler { ctx -> deleteAuthorByIdFails(ctx, emf) }

        //this is a PUT and we can use this to learn how to receive a JSON from the request body,
        //among other things we can send wrong contents in the JSON or partial contents. This will let us
        //experiment with exception handling the the route handler, or, better, with the 'failureHandler'
        mainRouter.put("/addNewAuthor").consumes("application/json").produces("application/json").handler { ctx -> addNewAuthor(ctx, emf) }

        //try to run the server on port 8080 on 0.0.0.0, using the router we have created to manage http end-points.
        //if anything fails an error will be shown...
        theServer.requestHandler(mainRouter).listen(8080) { http ->
          if (http.succeeded()) {
            startPromise.complete()
            println("HTTP server started on port 8080")
          } else {
            startPromise.fail(http.cause());
          }
      }
    }
  }

  //here we centralize our failure management
  private fun manageFailure(frc: RoutingContext) {
    //let provide a decent message back to the user/tester
    val response = frc.response()

    //as first store the vrt.x status code and the returned content type
    response.putHeader("content-type", "text/html")

    val uri: String = frc.normalisedPath()
    val codeString: String = frc.statusCode().toString()
    val error: String = frc.failure().localizedMessage
    val cause: String = frc.failure().cause.toString()

    //build the output by using multiline string literals (""")
    // and adding the HTML injected lang feature of IntelliJ (if you use it)

    //language=HTML
    return response.end("""
      <p><h3>Request to end point $uri failed with status code $codeString</h3></br>
      <h4>The internal server error was:</h4></br>
      $error</br>
      <h4>The internal exception (if any) was:</h4></br>
      $cause</p>
    """.trimIndent())
  }
}

/* HERE ARE SOME ROUTES EXAMPLES. DO NOT KEEP YOUR FUNCTION BODIES HERE, RATHER PUT THEM IN THE APPROPRIATE CLASSPATH */

@Entity
class Author {
  //in order to make hibernate generate the id we must add
  // a sequence in Postgres with the exact name of 'hibernate_sequence'
  @Id @GeneratedValue(strategy = GenerationType.AUTO)
  var id: Int = -1
  var first_name: String = ""
  var last_name: String = ""
  var nationality: String = ""
}

fun getAuthorById(ctx: RoutingContext, emf: EntityManagerFactory) {
  //get the 'id' parameter from the request
  val id: Int = ctx.request().getParam("id").toInt()

  val em = emf.createEntityManager()
  val ourAuthor: Author = em.find(Author::class.java, id)
  em.close() //close the connection

  val response = ctx.response()
  val payload = Json.encode(ourAuthor)

  return response.end(payload)
}

//this time we have no response to fill the body of: we just failed.
//by setting the fail filed we redirect to the failure handler
fun deleteAuthorByIdFails(ctx: RoutingContext, emf: EntityManagerFactory) {
  val response = ctx.fail(404)
}

//test with:
//try with a full json:
//curl -X PUT http://localhost:8080/addNewAuthor -H "Content-Type: application/json" -d "{\"first_name\":\"pippo\", \"last_name\":\"bill\", \"nationality\":\"belgian\"}"
//
//or try with an empty one...
//
//also try with a totally wrong JSON like:
//curl -X PUT http://localhost:8080/addNewAuthor -H "Content-Type: application/json" -d "{\"forecast\":\"cloudy\"}"
//or a mixed one!:
//curl -X PUT http://localhost:8080/addNewAuthor -H "Content-Type: application/json" -d "{\"forecast\":\"cloudy\", \"first_name\":\"pippo\"}"
fun addNewAuthor(ctx: RoutingContext, emf: EntityManagerFactory) {
  //get the payload
  val newAuthorInfoAsString = ctx.bodyAsString
  val newAuthor: Author = Json.decodeValue(newAuthorInfoAsString, Author::class.java)

  //the given data
  val em = emf.createEntityManager()
  em.transaction.begin();
  em.persist(newAuthor)
  em.flush()
  em.transaction.commit()
  em.close()

  return ctx.response().end(Json.encode(newAuthor))
}
