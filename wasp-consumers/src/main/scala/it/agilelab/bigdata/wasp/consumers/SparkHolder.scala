package it.agilelab.bigdata.wasp.consumers

import java.io.{File, FilenameFilter}

import com.typesafe.config.{Config, ConfigFactory}
import it.agilelab.bigdata.wasp.core.models.configuration.SparkConfigModel
import org.apache.spark.scheduler.{SparkListener, SparkListenerEnvironmentUpdate}
import org.apache.spark.{SparkConf, SparkContext}

object SparkHolder {
  private var sc: SparkContext = _

  private lazy val conf: Config = ConfigFactory.load

  /**
    * Try to initialize the SparkContext in the SparkHolder with the provided configuration.
    *
    * If the SparkContext does not exist, it will be created using the settings from sparkConfig,
    * and true will be returned.
    * If the SparkContext already exists, nothing will be done, and false will be returned.
    */
  def createSparkContext(sparkConfig: SparkConfigModel): Boolean =
    SparkHolder.synchronized {
      if (sc == null) {
        val loadedJars = sparkConfig.additionalJars.getOrElse(
          getAdditionalJar(Set(sparkConfig.yarnJar)))
        //TODO: gestire appName in maniera dinamica (e.g. batchGuardian, consumerGuardian..)
        val conf = new SparkConf()
          .setAppName(sparkConfig.appName)
          .setMaster(sparkConfig.master.toString)
          .set("spark.driver.cores", sparkConfig.driverCores.toString)
          .set("spark.driver.memory", sparkConfig.driverMemory) // NOTE: will only work in yarn-cluster
          .set("spark.driver.host", sparkConfig.driverHostname)
          .set("spark.driver.port", sparkConfig.driverPort.toString)
          .set("spark.executor.cores", sparkConfig.executorCores.toString)
          .set("spark.executor.memory", sparkConfig.executorMemory)
          .set("spark.executor.instances", sparkConfig.executorInstances.toString)
          .setJars(loadedJars)
          .set("spark.yarn.jar", sparkConfig.yarnJar)
          .set("spark.cleaner.ttl", sparkConfig.cleanerTtl.toString)
          .set("spark.blockManager.port", sparkConfig.blockManagerPort.toString)
          .set("spark.broadcast.port", sparkConfig.broadcastPort.toString)
          .set("spark.fileserver.port", sparkConfig.fileserverPort.toString)
          
        validateConfig(sparkConfig)

        val a = conf.getAll
        println("conf size: " + a.length)
        for (c <- conf.getAll) println("**************** " + c)

        sc = new SparkContext(conf)
        sc.setLogLevel("WARN")

        true
      } else {
        false
      }
    }

  /**
    * Returns the SparkContext held by this SparkHolder, or throws an exception if it was not initialized.
    */
  def getSparkContext = {
    if (sc == null) {
      throw new Exception("The SparkContext was not initialized")
    }
    sc
  }

  def getAdditionalJar(skipJars: Set[String]) = {

    val additionalJarsPath = conf.getString("default.additionalJars.path")

    val fm = getListOfFiles(s"${additionalJarsPath}/managed/")
    val f = getListOfFiles(s"${additionalJarsPath}/")

    val allFilesSet = fm
        .map(f => s"${additionalJarsPath}/managed/${f}")
        .toSet[String] ++ f.map(f => s"${additionalJarsPath}/${f}").toSet[String]

    val realFiles = allFilesSet.diff(skipJars)

    realFiles.toSeq
  }

  def getListOfFiles(dir: String): List[String] = {

    val d = new File(dir)

    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).map(_.getName).toList
    } else {
      List[String]()
    }
  }
  
  def validateConfig(sparkConfig: SparkConfigModel): Unit = {
    val master = sparkConfig.master
    if (master.protocol == "" && master.host.startsWith("yarn"))
      println("Running on YARN without specifying spark.yarn.jar is unlikely to work!")
  }

}
