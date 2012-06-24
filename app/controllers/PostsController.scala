package controllers

import java.util.UUID
import scala.concurrent.stm._
import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.validation.Constraints._
import views.html.defaultpages.notFound
import events._
import models._
import support.Mappings._

object PostsController extends Controller {

  /**
   * A Scala STM reference holding the current state of the application, derived from all committed events.
   */
  val posts = {
    // Handy test data.
    val initialEvents = Seq(
      PostCreated(UUID.fromString("4e885ffe-870e-45b4-b5dd-f16d381d6f6a"), PostContent("Erik", "Scala is awesome", "Scala...")),
      PostCreated(UUID.fromString("4e885ffe-870e-45b4-b5dd-f16d381d6f6f"), PostContent("Bas", "Righteous Ruby", "Ruby...")))
    val initialValue = initialEvents.foldLeft(Posts())(_ apply _)
    Ref(initialValue).single
  }

  /**
   * Commits an event and applies it to the current state.
   */
  def commit(event: PostEvent) = {
    posts.transform(_.apply(event))
    Logger.debug("Committed event: " + event)
  }

  /**
   * Show an overview of the most recent blog posts.
   */
  def index = Action { implicit request =>
    Ok(views.html.posts.index(posts().mostRecent(20)))
  }

  /**
   * Show a specific blog post.
   */
  def show(id: UUID) = Action { implicit request =>
    posts().get(id) match {
      case Some(post) => Ok(views.html.posts.show(post))
      case None       => NotFound(notFound(request, None))
    }
  }

  /**
   * Show the form to create a new blog post.
   */
  def renderCreate = Action { implicit request =>
    Ok(views.html.posts.create(UUID.randomUUID, postContentForm))
  }

  /**
   * Process the new blog post form.
   */
  def submitCreate(id: UUID) = Action { implicit request =>
    postContentForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.posts.create(id, formWithErrors)),
      postContent => {
        commit(PostCreated(id, postContent))
        Redirect(routes.PostsController.show(id)).flashing("info" -> "Post created.")
      })
  }

  /**
   * Show the form to edit an existing blog post.
   */
  def renderEdit(id: UUID) = Action { implicit request =>
    posts().get(id) match {
      case Some(post) => Ok(views.html.posts.edit(id, postContentForm.fill(post.content)))
      case None       => NotFound(notFound(request, None))
    }
  }

  /**
   * Process an edited blog post.
   */
  def submitEdit(id: UUID) = Action { implicit request =>
    postContentForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.posts.edit(id, formWithErrors)),
      postContent => {
        commit(PostUpdated(id, postContent))
        Redirect(routes.PostsController.show(id)).flashing("info" -> "Post saved.")
      })
  }

  /**
   * Delete a blog post.
   */
  def delete(id: UUID) = Action { implicit request =>
    commit(PostDeleted(id))
    Redirect(routes.PostsController.index).flashing("info" -> "Post deleted.")
  }

  /*
   * Blog content form definition.
   */
  private val postContentForm = Form(mapping(
    "author" -> trimmedText.verifying(minLength(3)),
    "title" -> trimmedText.verifying(minLength(3)),
    "content" -> trimmedText.verifying(minLength(3)))(PostContent.apply)(PostContent.unapply))
}
