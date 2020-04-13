/*
THIS IS THE FIFITH EXAMPLE OF HOW TO USE VERT.X WEB. This is neither kotlin specific nor vert.x specific
this is an exercise to look at the JPA/Jackson synthax in a one-to-One JPA relation in kotlin
*/

package it.nunziatimatteo.skel

import com.fasterxml.jackson.annotation.JsonIdentityInfo
import com.fasterxml.jackson.annotation.ObjectIdGenerators
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

    vertx.executeBlocking({ promise: Promise<Any> ->

      val emf = Persistence.createEntityManagerFactory( "org.hibernate.tutorial.jpa" )
      promise.complete(emf) //put emf into a promise

    }) {
      promiseExecution ->

        val emf = promiseExecution.result() as EntityManagerFactory

        mainRouter.get("/authorById/:id").produces("text/json").handler { ctx -> getAuthorById(ctx, emf) }

        //NEW NEW NEW - added a new method
        mainRouter.get("/scoreById/:id").produces("text/json").handler { ctx -> getScoreById(ctx, emf) }
        mainRouter.delete("/deleteAuthorByIdFails/:id").handler { ctx -> deleteAuthorByIdFails(ctx, emf) }
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
//this is used to tell the json serializer (jackson) to cut possible recursions caused by
//what is known as "bidirectional relation" in JPA
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator::class, property = "id")
class Author {
  @Id @GeneratedValue(strategy = GenerationType.AUTO)
  var id: Int = -1
  var first_name: String = ""
  var last_name: String = ""
  var nationality: String = ""

  //this means that there is a table somewhare that we have mapped with the UserScore class
  //and that refers to this entity by using a foreign key.
  //One good thing of JPA is that we can ask to revert the relation and get the record referencing us!
  //anyway this leads to a recursion - hence the @Json... decorator to cut recursion
  @OneToOne(mappedBy = "author")
  var score: UserScore? = null
}

//NEW NEW NEW
@Entity
@Table(name="user_score")
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator::class, property = "score")
class UserScore {
  @Id @GeneratedValue(strategy = GenerationType.AUTO)
  var id: Int = -1
  var score: Int = 0

  //this is a foreign key in JPA speaking
  //as we map this relation here but also in the reversed class Author, we need a Json decorator here too...
  @OneToOne()
  @JoinColumn(name = "author_id", referencedColumnName = "id")
  var author: Author = Author()
}

fun getAuthorById(ctx: RoutingContext, emf: EntityManagerFactory) {
  val id: Int = ctx.request().getParam("id").toInt()

  val em = emf.createEntityManager()
 val ourAuthor: Author = em.find(Author::class.java, id)

  val response = ctx.response()
  val payload = Json.encode(ourAuthor)

  em.close()

  return response.end(payload)
}

//NEW NEW NEW - as per author but expects to find something into a table named "user_score"
fun getScoreById(ctx: RoutingContext, emf: EntityManagerFactory) {
  val id: Int = ctx.request().getParam("id").toInt()

  val em = emf.createEntityManager()
  val ourScore: UserScore = em.find(UserScore::class.java, id)

  val response = ctx.response()
  val payload = Json.encode(ourScore)

  em.close()

  return response.end(payload)
}

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
