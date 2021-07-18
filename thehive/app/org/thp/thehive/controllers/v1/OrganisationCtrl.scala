package org.thp.thehive.controllers.v1

import org.thp.scalligraph.EntityIdOrName
import org.thp.scalligraph.controllers.{Entrypoint, FieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperties, Query}
import org.thp.scalligraph.traversal.{IteratorOutput, Traversal}
import org.thp.thehive.controllers.v1.Conversion._
import org.thp.thehive.dto.v1.InputOrganisation
import org.thp.thehive.models._
import org.thp.thehive.services._
import play.api.mvc.{Action, AnyContent, Handler, Results}

import scala.util.Success

class OrganisationCtrl(
    entrypoint: Entrypoint,
    properties: Properties,
    organisationSrv: OrganisationSrv,
    userSrv: UserSrv,
    implicit val db: Database
) extends QueryableCtrl
    with TheHiveOpsNoDeps {

  override val entityName: String                 = "organisation"
  override val publicProperties: PublicProperties = properties.organisation
  override val initialQuery: Query =
    Query.init[Traversal.V[Organisation]](
      "listOrganisation",
      (graph, authContext) =>
        organisationSrv
          .startTraversal(graph)
          .visible(authContext)
    )
  override val pageQuery: ParamQuery[OutputParam] = Query.withParam[OutputParam, Traversal.V[Organisation], IteratorOutput](
    "page",
    (range, organisationSteps, authContext) =>
      organisationSteps.richPage(range.from, range.to, range.extraData.contains("total"))(_.richOrganisation(authContext))
  )
  override val outputQuery: Query = Query.outputWithContext[RichOrganisation, Traversal.V[Organisation]](_.richOrganisation(_))
  override val getQuery: ParamQuery[EntityIdOrName] = Query.initWithParam[EntityIdOrName, Traversal.V[Organisation]](
    "getOrganisation",
    (idOrName, graph, authContext) => organisationSrv.get(idOrName)(graph).visible(authContext)
  )
  override val extraQueries: Seq[ParamQuery[_]] = Seq(
    Query[Traversal.V[Organisation], Traversal.V[Organisation]]("visible", (organisationSteps, _) => organisationSteps.visibleOrganisations),
    Query[Traversal.V[Organisation], Traversal.V[User]]("users", (organisationSteps, _) => organisationSteps.users.dedup),
    Query[Traversal.V[Organisation], Traversal.V[CaseTemplate]]("caseTemplates", (organisationSteps, _) => organisationSteps.caseTemplates),
    Query[Traversal.V[Organisation], Traversal.V[Alert]]("alerts", (organisationSteps, _) => organisationSteps.alerts)
  )

  def create: Action[AnyContent] =
    entrypoint("create organisation")
      .extract("organisation", FieldsParser[InputOrganisation])
      .authPermittedTransaction(db, Permissions.manageOrganisation) { implicit request => implicit graph =>
        val inputOrganisation: InputOrganisation = request.body("organisation")
        for {
          user         <- userSrv.current.getOrFail("User")
          organisation <- organisationSrv.create(inputOrganisation.toOrganisation, user)
        } yield Results.Created(organisation.toJson)
      }

  def get(organisationId: String): Action[AnyContent] =
    entrypoint("get organisation")
      .authRoTransaction(db) { implicit request => implicit graph =>
        (if (organisationSrv.current.isAdmin)
           organisationSrv.get(EntityIdOrName(organisationId))
         else
           userSrv
             .current
             .organisations
             .visibleOrganisations
             .get(EntityIdOrName(organisationId)))
          .richOrganisation
          .getOrFail("Organisation")
          .map(organisation => Results.Ok(organisation.toJson))
      }

  def update(organisationId: String): Action[AnyContent] =
    entrypoint("update organisation")
      .extract("organisation", FieldsParser.update("organisation", properties.organisation))
      .authPermittedTransaction(db, Permissions.manageOrganisation) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("organisation")
        for {
          organisation <- organisationSrv.getOrFail(EntityIdOrName(organisationId))
          _            <- organisationSrv.update(organisationSrv.get(organisation), propertyUpdaters)
        } yield Results.NoContent
      }

  def listSharingProfiles: Action[AnyContent] =
    entrypoint("list sharing profiles")
      .auth { _ =>
        Success(Results.Ok(organisationSrv.sharingProfiles.toJson))
      }

}
