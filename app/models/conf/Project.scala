package models.conf

import enums.LevelEnum
import play.api.Logger
import play.api.Play.current
import models.PlayCache
import org.joda.time.DateTime

import scala.slick.driver.MySQLDriver.simple._
import com.github.tototoshi.slick.MySQLJodaSupport._

import scala.slick.jdbc.JdbcBackend

/**
 * 项目
 * 项目为综合型，通过类型标示不同
 *
 * @author of546
 */
case class Project(id: Option[Int], name: String, templateId: Int, subTotal: Int, lastVid: Option[Int], lastVersion: Option[String], lastUpdated: Option[DateTime])
case class ProjectForm(id: Option[Int], name: String, templateId: Int, subTotal: Int, lastVid: Option[Int], lastVersion: Option[String], lastUpdated: Option[DateTime], items: List[Attribute]) {
  def toProject = Project(id, name, templateId, subTotal, lastVid, lastVersion, lastUpdated)
}

class ProjectTable(tag: Tag) extends Table[Project](tag, "project") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name")
  def templateId = column[Int]("template_id")           // 项目模板编号
  def subTotal = column[Int]("sub_total", O.Default(0)) // 版本数量
  def lastVid = column[Int]("last_version_id", O.Nullable)         // 最近版本id
  def lastVersion = column[String]("last_version", O.Nullable)     // 最近版本号
  def lastUpdated= column[DateTime]("last_updated", O.Nullable, O.Default(DateTime.now()))

  override def * = (id.?, name, templateId, subTotal, lastVid.?, lastVersion.?, lastUpdated.?) <> (Project.tupled, Project.unapply _)
  def idx = index("idx_name", name, unique = true)
  def idx_template = index("idx_template", templateId)
}

object ProjectHelper extends PlayCache {

  import models.AppDB._

  val qProject = TableQuery[ProjectTable]
  val qMember = TableQuery[MemberTable]

  def findById(id: Int): Option[Project] = db withSession { implicit session =>
    qProject.filter(_.id === id).firstOption
  }

  def findByName(name: String): Option[Project] = db withSession { implicit session =>
    qProject.filter(_.name === name).firstOption
  }

  def countByTemplateId(templateId: Int) = db withSession { implicit session =>
    qProject.filter(_.templateId === templateId).length.run
  }

  def count(jobNo: Option[String]): Int = db withSession { implicit session =>
    jobNo match {
      case Some(no) =>
        val query = (for {
          p <- qProject
          m <- qMember if p.id === m.projectId
        } yield (p, m)).filter(_._2.jobNo === jobNo)
        query.length.run
      case None =>
        qProject.length.run
    }
  }

  def all(jobNo: Option[String], page: Int, pageSize: Int): Seq[Project] = db withSession { implicit session =>
    val offset = pageSize * page
    jobNo match {
      case Some(no) =>
        val query = (for {
          p <- qProject
          m <- qMember if p.id === m.projectId
        } yield (p, m)).filter(_._2.jobNo === jobNo)
        query.map(_._1).drop(offset).take(pageSize).list
      case None =>
        qProject.drop(offset).take(pageSize).list
    }
  }

  def all(): Seq[Project] = db withSession { implicit session =>
    qProject.list
  }

  def create(project: Project) = db withSession { implicit session =>
    _create(project)
  }

  def create(projectForm: ProjectForm, jobNo: String) = db withTransaction { implicit session =>
    val pid = _create(projectForm.toProject)
    val attrs = projectForm.items.map(item => item.copy(None, Some(pid)))
    AttributeHelper._create(attrs)
    MemberHelper._create(Member(None, pid, LevelEnum.safe, jobNo))
  }

  def _create(project: Project)(implicit session: JdbcBackend#Session) = {
    qProject.returning(qProject.map(_.id)).insert(project)(session)
  }

  def delete(id: Int) = db withSession { implicit session =>
    qProject.filter(_.id === id).delete
  }

  def update(id: Int, projectForm: ProjectForm) = db withSession { implicit session =>
    AttributeHelper._deleteByProjectId(id)
    val attrs = projectForm.items.map(item => item.copy(None, Some(id)))
    AttributeHelper._create(attrs)
    _update(id, projectForm.toProject)
  }

  def _update(id: Int, project: Project)(implicit session: JdbcBackend#Session) = {
    val project2update = project.copy(Some(id))
    qProject.filter(_.id === id).update(project2update)(session)
  }

}
