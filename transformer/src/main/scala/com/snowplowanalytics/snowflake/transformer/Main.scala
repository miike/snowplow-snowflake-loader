/*
 * Copyright (c) 2017 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowflake.transformer

import org.apache.spark.{SparkConf, SparkContext}
import com.snowplowanalytics.snowflake.core.{ ProcessManifest, Config }


object Main {
  def main(args: Array[String]): Unit = {
    Config.parseTransformerCli(args) match {
      case Some(Right(Config.CliTransformerConfiguration(appConfig))) =>

        // Always use EMR Role role for manifest-access
        val s3 = ProcessManifest.getS3(appConfig.awsRegion)
        val dynamoDb = ProcessManifest.getDynamoDb(appConfig.awsRegion)
        val manifest = ProcessManifest.AwsProcessingManifest(s3, dynamoDb)

        // Eager SparkContext initializing to avoid YARN timeout
        val config = new SparkConf()
          .setAppName("snowflake-transformer")
          .setIfMissing("spark.master", "local[*]")
        val sc = new SparkContext(config)

        // Get run folders that are not in RunManifest in any form
        val runFolders = manifest.getUnprocessed(appConfig.manifest, appConfig.input)

        runFolders match {
          case Right(folders) =>
            val configs = folders.map(TransformerJobConfig(appConfig.input, appConfig.stageUrl, _))
            TransformerJob.run(sc, manifest, appConfig.manifest, configs)
          case Left(error) =>
            println("Cannot get list of unprocessed folders")
            println(error)
            sys.exit(1)
        }


      case Some(Left(error)) =>
        // Failed transformation
        println(error)
        sys.exit(1)

      case None =>
        // Invalid arguments
        sys.exit(1)
    }
  }
}
