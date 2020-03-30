/*
THIS IS THE THIRD EXAMPLE OF HOW TO USE VERT.X WEB. YOU CAN TEST THE ROUTES VIA CURL OR SIMILAR
here we introduce a JPA backend. for this task we have added the kotlin-maven-plugin in the maven pom.xml file! check it!
in the same pom.xml we have added Hibernate as JPA implementation.
Eventually we have added the /src/main/resources/META-INF/persistence.xml required to config JPA connections

NOTE: the persistance.xml file required by JPA expect you to have a local postgres with a db named test-db and the following auth:
user: test-user
password: test-users
a dump of the initial DB is available in the root of these sources (Postgres 12) as 'vert.x-web-howto-DB-exercise-3'

NOTE2: Hibernate is a blocking technology, you will see some complains (java exceptions) at boot due to vert.x,
don't mind we will fix them in next exercise

Just a few numbers from my 5yo thinkpad T440:

non JPA fat jar
---------------
size on disk: 11 MB
ram (2 requests): 75 MB
boot in:  around 4 secs

JPA fat jar
-----------
size on disk: 25 MB (26 with the jdbc but this is required anyway)
ram (no requests just connection): 370 MB
boot in:  around 12 secs

Be straight: I hate ORM but I need this for my job. so here we are!
*/

package it.nunziatimatteo.skel

import io.vertx.core.AbstractVerticle
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
    val emf = Persistence.createEntityManagerFactory( "org.hibernate.tutorial.jpa" ) //init JPA from persistence.xml
    
    /* NEW NEW NEW */
    // since now we can think of passing always 2 args to the route handlers: the context (ctx) and the entity manager factory (emf)
    // in that way they will be able to manipulate both REST and CRUD!
    // if the em is not required don't worry, this is passed by ref and will not hurt

  /* ------------------------------------------------------------------------------------------------------------------ */
  /* here we can put our routes, that is the relation between an http end-point and a function responsible to manage it */
  /* ------------------------------------------------------------------------------------------------------------------ */

    //this time we mock up a request info about a book's author by knowing her id. the response is a JSON.
    mainRouter.get("/authorById/:id").produces("text/json").handler { ctx -> getAuthorById(ctx, emf) }

    //this time we are asked to DELETE an author by id, this is useful to mock an error of a request
    //note that we do not produce anything here. we just return an error status. This error status voluntarily redirects to the failure handler
    mainRouter.delete("/deleteAuthorByIdFails/:id").handler { ctx -> deleteAuthorByIdFails(ctx, emf) }

    /* NEW NEW NEW */
    //this time we return the json with the persisted object!

    //this is a PUT and we can use this to learn how to receive a JSON from the request body,
    //this also make use of the new installed 'BodyHandler'.
    //among other things we can send wrong contents in the JSON or partial contents. This will let us
    //experiment with exception handling the the route handler, or, better, with the 'failureHandler'
    mainRouter.put("/addNewAuthor").consumes("application/json").produces("application/json").handler { ctx -> addNewAuthor(ctx, emf) }

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
      <p><h3>Request to end point $uri failed with status code $codeString</h3></br>
      <h4>The internal server error was:</h4></br>
      $error</br>
      <h4>The internal exception (if any) was:</h4></br>
      $cause</p>
    """.trimIndent())
  }
}

/* HERE ARE SOME ROUTES EXAMPLES. DO NOT KEEP YOUR FUNCTION BODIES HERE, RATHER PUT THEM IN THE APPROPRIATE CLASSPATH */

/* NEW NEW NEW */
//we have decorated the Author class with the JPA @Entity decorator
//to let JPA manage it! So this will be mapped to the author table!!!
@Entity
class Author {
  //notify JPA this is the PK and must be autogenerated by hibernate...
  //... in order to make hibernate generate the id we must add
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

  /* NEW NEW NEW */
  val em = emf.createEntityManager() //create the central manipulation object for JPA operations (e.g. CRUD)

  //query postgres for our author by using JPA -
  val ourAuthor: Author = em.find(Author::class.java, id)

  /* NEW NEW NEW */
  em.close() //close the connection

  val response = ctx.response()
  //convert our POJO in a stringified JSON. you can think about this as a 2-phase transform:
  // 1- convert a POJO into a JSON
  // 2- does the same as JSON.stringify of JS
  val payload = Json.encode(ourAuthor)

  return response.end(payload)
}

//this time we have no response to fill the body of: we just failed.
//by setting the fail filed we redirect to the failure handler
fun deleteAuthorByIdFails(ctx: RoutingContext, emf: EntityManagerFactory) {
  val response = ctx.fail(404)
}

/* NEW NEW NEW */
//added actual JPA persistance, removed id from JSON as it is autogenerated!!!!

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
  //this is the opposite of getAuthorById:
  //we convert the stringified JSON in a JSON object, we map it on our class!
  val newAuthor: Author = Json.decodeValue(newAuthorInfoAsString, Author::class.java)

  /* store */

  /* NEW NEW NEW */
  val em = emf.createEntityManager() //create the central manipulation object for JPA operations (e.g. CRUD)

  //1- open a transaction
  em.transaction.begin();
  //2- ask to manage this object
  em.persist(newAuthor)
  //3- actually INSERT it in DB
  em.flush()
  //4- commit the transaction
  em.transaction.commit()

  em.close() //close the connection

  //now that the author has been persisted, Hibernate has defined her id, return it in the body

  //nothing to do with response just return an empty one with default status of 200
  return ctx.response().end(Json.encode(newAuthor))
}
