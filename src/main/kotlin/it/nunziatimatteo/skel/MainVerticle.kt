/*
THIS IS THE SECOND EXAMPLE OF HOW TO USE VERT.X WEB. YOU CAN TEST THE ROUTES VIA CURL OR SIMILAR
e.g. the 'deleteAuthorByIdFails' can be reached by: curl -X DELETE http://http://localhost:8080/deleteAuthorByIdFails/22
 */

package it.nunziatimatteo.skel

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.Json
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.ResponseContentTypeHandler

class MainVerticle : AbstractVerticle() {

  override fun start(startPromise: Promise<Void>) {
    val theServer = vertx.createHttpServer() //the one and only http server of our app.
    val mainRouter = Router.router(vertx) //this is the router responsible to 'attach' a function to each http end-point
    mainRouter.route("/*").handler(ResponseContentTypeHandler.create()) //tell vert.x that we want it to automatically set the header of our responses

    /* NEW NEW NEW */
    mainRouter.route().handler(BodyHandler.create()) //THIS IS NEW AND IS USED TO EXTRACT BODIES FROM REQUESTS
                                                     //note you can either use route("/*") or route() to mean 'all routes'

    /* NEW NEW NEW */
    mainRouter.route().failureHandler { frc -> this.manageFailure(frc) } // This will be called for failures that occur when routing

  /* ------------------------------------------------------------------------------------------------------------------ */
  /* here we can put our routes, that is the relation between an http end-point and a function responsible to manage it */
  /* ------------------------------------------------------------------------------------------------------------------ */

    //this time we mock up a request info about a book's author by knowing her id. the response is a JSON.
    mainRouter.get("/authorById/:id").produces("text/json").handler { ctx -> getAuthorById(ctx) }

    //this time we are asked to DELETE an author by id, this is useful to mock an error of a request
    //note that we do not produce anything here. we just return an error status. This error status voluntarily redirects to the failure handler
    mainRouter.delete("/deleteAuthorByIdFails/:id").handler { ctx -> deleteAuthorByIdFails(ctx) }

    //this is a PUT and we can use this to learn how to receive a JSON from the request body,
    //this also make use of the new installed 'BodyHandler'.
    //among other things we can send wrong contents in the JSON or partial contents. This will let us
    //experiment with exception handling the the route handler, or, better, with the 'failureHandler'
    mainRouter.put("/addNewAuthor").consumes("application/json").handler { ctx -> addNewAuthor(ctx) }

  /* ------------------------------------------------------------------------------------------------------------------ */
  /* ------------------------------------------------------------------------------------------------------------------ */


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

  //here we centralize our failure management. PLEASE NOTE: failure handlers can be assigned
  //in more fine grained ways. each and every route can have both a handler and a failure handler
  //
  //Also please not that in a real app you would use this to redirect to a custom error page...
  private fun manageFailure(frc: RoutingContext) {
    //let provide a decent message back to the user/tester
    val response = frc.response()

    //as first store the vrt.x status code and the returned content type
    response.putHeader("content-type", "text/html")

    /* build a proper message */

    //get the request URI causing the issue
    val uri: String = frc.normalisedPath()
    val codeString: String = frc.statusCode().toString()

    //write the reason of the failure
    val error: String = frc.failure().localizedMessage

    //the get also the cause
    val cause: String = frc.failure().cause.toString()

    //build the output by using multiline string literals (""")
    // and adding the HTML injected lang feature of IntelliJ (if you use it)

    //language=HTML
    return response.end("""
      <h3>Request to end point $uri failed with status code $codeString</h3></br>
      <h4>The internal server error was:</h4></br>
      <p><strong>$error</strong></p></br>
      <h4>The internal exception (if any) was:</h4></br>
      <p>$cause</p>
    """.trimIndent())
  }
}

/* HERE ARE SOME ROUTES EXAMPLES. DO NOT KEEP YOUR FUNCTION BODIES HERE, RATHER PUT THEM IN THE APPROPRIATE CLASSPATH */

/* NEW NEW NEW */
//we have modified the author class to let encode AND DECODE IT.
//This is no more a data class but a simple class!!! Json.decode() is not able to manage data classes!!!! (see the PUT example)
class Author {
  var id: Int = -1
  var first_name: String = ""
  var last_name: String = ""
  var nationality: String = ""
}

fun getAuthorById(ctx: RoutingContext) {
  //get the 'id' parameter from the request
  val id: Int = ctx.request().getParam("id").toInt()

  //here we should make some query in some DB and get back the info...
  //... let pretend we have and just populate a POJO
  val ourAuthor = Author()
  ourAuthor.id = id
  ourAuthor.first_name = "John"
  ourAuthor.last_name = "Grisham"
  ourAuthor.nationality = "UK"

  val response = ctx.response()
  //convert our POJO in a stringified JSON. you can think about this as a 2-phase transform:
  // 1- convert a POJO into a JSON
  // 2- does the same as JSON.stringify of JS
  val payload = Json.encode(ourAuthor)

  return response.end(payload)
}

//this time we have no response to fill the body of: we just failed.
//by setting the fail filed we redirect to the failure handler
fun deleteAuthorByIdFails(ctx: RoutingContext) {
  val response = ctx.fail(404)
}

//test with:
// curl -X PUT http://localhost:8080/addNewAuthor -H "Content-Type: application/json" -d "{\"id\":\"40\"}"
// to see what happens with a partial info.
//
//or try with a full json:
//curl -X PUT http://localhost:8080/addNewAuthor -H "Content-Type: application/json" -d "{\"id\":\"40\", \"first_name\":\"pippo\", \"last_name\":\"bill\", \"nationality\":\"belgian\"}"
//
//or try with an empty one...
//
//also try with a totally wrong JSON like:
//curl -X PUT http://localhost:8080/addNewAuthor -H "Content-Type: application/json" -d "{\"forecast\":\"cloudy\"}"
//or a mixed one!:
//curl -X PUT http://localhost:8080/addNewAuthor -H "Content-Type: application/json" -d "{\"forecast\":\"cloudy\", \"first_name\":\"pippo\"}"
fun addNewAuthor(ctx: RoutingContext) {
  //get the payload
  val newAuthorInfoAsString = ctx.bodyAsString
  //this is the opposite of getAuthorById:
  //we convert the stringified JSON in a JSON object, we map it on our class!
  val newAuthor: Author = Json.decodeValue(newAuthorInfoAsString, Author::class.java)

  //nothing to do with response just return an empty one with default status of 200
  return ctx.response().end()
}
