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

import org.apache.spark.SparkContext

import com.snowplowanalytics.snowflake.core.ProcessManifest

object TransformerJob {

  /** Process all directories, saving state into DynamoDB */
  def run(sc: SparkContext, manifest: ProcessManifest, tableName: String, jobConfigs: List[TransformerJobConfig]): Unit = {
    jobConfigs.foreach { jobConfig =>
      println(s"Snowflake Transformer: processing ${jobConfig.runId}. ${System.currentTimeMillis()}")
      manifest.add(tableName, jobConfig.runId)
      val shredTypes = process(sc, jobConfig)
      manifest.markProcessed(tableName, jobConfig.runId, shredTypes, jobConfig.output)
      println(s"Snowflake Transformer: processed ${jobConfig.runId}. ${System.currentTimeMillis()}")
    }
  }

  /**
   * Transform particular folder to Snowflake-compatible format and
   * return list of discovered shredded types
   *
   * @param sc existing spark context
   * @param jobConfig configuration with paths
   * @return list of discovered shredded types
   */
  def process(sc: SparkContext, jobConfig: TransformerJobConfig) = {
    val keysAggregator = new StringSetAccumulator
    sc.register(keysAggregator)

    val events = sc.textFile(jobConfig.input)

    val snowflake = events.map { event =>
      Transformer.transform(event) match {
        case (keys, transformed) =>
          keysAggregator.add(keys)
          transformed
      }
    }

    snowflake.saveAsTextFile(jobConfig.output)

    val keysFinal = keysAggregator.value.toList
    println(s"Shred types for  ${jobConfig.runId}: " + keysFinal.mkString(", "))
    keysAggregator.reset()
    keysFinal
  }
}
