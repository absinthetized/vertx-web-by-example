/*
THIS IS THE FIRST EXAMPLE OF HOW TO USE VERT:X WEB. YOU CAN TEST THE ROUTES OF THIS FILE FROM YOUR BROWSER AS THEY ARE ONLY HTTP GETs
 */

package it.nunziatimatteo.skel

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.Json
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.ResponseContentTypeHandler

//we need to derive from an AbstractVerticle class to start the app.
class MainVerticle : AbstractVerticle() {

  //this is the default way to tell the app we have to 'start' from here...
  override fun start(startPromise: Promise<Void>) {
    val theServer = vertx.createHttpServer() //this is val, that is a constant, and it is the one and only http server of our app.
    val mainRouter = Router.router(vertx) //again a constant, this is the router responsible to 'attach' a function to each http end-point
    mainRouter.route("/*").handler(ResponseContentTypeHandler.create()) //tell vert.x that we want it to automatically set the header of our responses, based on the 'produces()' clause. see routes examples.


  /* ------------------------------------------------------------------------------------------------------------------ */
  /* here we can put our routes, that is the relation between an http end-point and a function responsible to manage it */
  /* ------------------------------------------------------------------------------------------------------------------ */

    //an hello world example. this is an http GET for the uri localhost:8080/hello-world
    //so we use the get("hello-world") to create the route.
    //we also say that the output (the product) is in plain text, so we set the produces("text/plain")
    //eventually we pass the route to an handler.
    //the handler is super simple: it is a kotlin lambda. kotlin lambdas have:
    // - 2 curly brackets {}
    // - an input (ctx)
    // - an arrow '->'
    // - a function body.
    //it is good practice to make the lambda body a function on its turn. here we use the getHelloWorld() function. see it for more details
    mainRouter.get("/hello-world").produces("text/plain").handler { ctx -> getHelloWorld(ctx) }

    //this is a prettier example: still a GET, but we expect to have a parameter in the uri: a name.
    //also we expect to return a nice HTML response.
    mainRouter.get("/hello-world/:name").produces("text/html").handler { ctx -> getNamedHelloWorldInHTML(ctx) }

    //this time we mock up a request info about a book's author by knowing her id. the response is a JSON
    //this will let us use the underlying Jackson library to map POJOs to JSON
    mainRouter.get("/authorById/:id").produces("text/json").handler { ctx -> getAuthorById(ctx) }


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
}

/* HERE ARE SOME ROUTES EXAMPLES. DO NOT KEEP YOUR FUNCTION BODIES HERE, RATHER PUT THEM IN THE APPROPRIATE CLASSPATH */

//anything can be a route handler, there is no constrain, but usually you want the handler to be a function with the routing context as input
//and a routing response as output.
//the routing context, or ctx for short, is an object containing all the relevant info about an http request. As relevant examples,
//it contains the request with its header and body, it allows us to create a response an also contains auth info - if available.
//the function MUST return a response and the response object must by finalized with the end() method before returning it.
fun getHelloWorld(ctx: RoutingContext) // the return type is inferred
{
  //allocate a response for the given context
  val response = ctx.response()
  //put some content into the body. no need to specify the content type
  // as we have the ResponseContentTypeHandler.create() set globally for all routes
  return response.end("Hello world from Vert.x!")
}

fun getNamedHelloWorldInHTML(ctx: RoutingContext) {
  //get the 'name' parameter from the request - this time we have to explicitly inform kotlin that this is a string
  val givenName: String = ctx.request().getParam("name")

  val response = ctx.response()
  //note the HTML tags in the response. Also note that we can use 'macro substitution' to format the return text
  return response.end("<h3>Hello $givenName from Vert.x!</h3>")
}

//a data class is a class with only members, no methods... if you come from Java this is like a @Data decorated class with Lombok
//this is an example of how you can build a data class in kotlin, even if there are more nice ways when you start using plugins for JPA and similar stuff
//a data class must have at least 1 param in the constructor to identify it. other params can be placed into the c'tor or in the class body
data class Author(var id: Int) {
  lateinit var first_name: String //lateinit means we do not default init and we expect to init this stuff later
  lateinit var last_name: String
  lateinit var nationality: String
}

fun getAuthorById(ctx: RoutingContext) {
  //get the 'id' parameter from the request
  val id: Int = ctx.request().getParam("id").toInt()

  //here we should make some query in some DB and get back the info...
  //... let pretend we have and just populate a POJO
  val ourAuthor = Author(id=0)
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
