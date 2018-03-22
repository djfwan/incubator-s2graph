package org.apache.s2graph.s2jobs

import org.apache.spark.sql.SparkSession
import play.api.libs.json.{JsValue, Json}

import scala.io.Source

case class JobOption(
                      name:String = "S2BatchJob",
                      confType:String = "db",
                      jobId:Int = -1,
                      confFile:String = ""
                    )

object JobLauncher extends Logger {

  def parseArguments(args: Array[String]): JobOption = {
    val parser = new scopt.OptionParser[JobOption]("run") {
      opt[String]('n', "name").required().action((x, c) =>
        c.copy(name = x)).text("job display name")

      cmd("file").action((_, c) => c.copy(confType = "file"))
        .text("get config from file")
        .children(
          opt[String]('f', "confFile").required().valueName("<file>").action((x, c) =>
            c.copy(confFile = x)).text("configuration file")
        )

      cmd("db").action((_, c) => c.copy(confType = "db"))
        .text("get config from db")
        .children(
          opt[String]('i', "jobId").required().valueName("<jobId>").action((x, c) =>
            c.copy(jobId = x.toInt)).text("configuration file")
        )
    }

    parser.parse(args, JobOption()) match {
      case Some(o) => o
      case None =>
        parser.showUsage()
        throw new IllegalArgumentException(s"failed to parse options... (${args.mkString(",")}")
    }
  }

  def getConfig(options: JobOption):JsValue = options.confType match {
    case "file" =>
      Json.parse(Source.fromFile(options.confFile).mkString)
    case "db" =>
      throw new IllegalArgumentException(s"'db' option that read config file from database is not supported yet.. ")
  }

  def main(args: Array[String]): Unit = {

    val options = parseArguments(args)
    logger.info(s"Job Options : ${options}")

    val jobDescription = JobDescription(getConfig(options))

    val ss = SparkSession
      .builder()
      .appName(s"${jobDescription.name}")
      .config("spark.driver.maxResultSize", "20g")
      .enableHiveSupport()
      .getOrCreate()

    val job = new Job(ss, jobDescription)
    job.run()
  }
}
