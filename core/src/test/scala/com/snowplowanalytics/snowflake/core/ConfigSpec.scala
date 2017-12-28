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
package com.snowplowanalytics.snowflake.core

import org.specs2.Specification

import com.snowplowanalytics.snowflake.core.Config.S3Folder.{coerce => s3}
import com.snowplowanalytics.snowflake.core.Config.CliLoaderConfiguration

class ConfigSpec extends Specification { def is = s2"""
  Parse valid setup configuration $e1
  Parse valid load configuration $e2
  Parse valid base64-encoded configuration $e3
  Parse valid S3 without trailing slash $e4
  Parse valid S3 with trailing slash and s3n scheme $e5
  Fail to parse invalid scheme $e6
  Parse valid base64-encoded configuration with roleArn $e7
  Parse valid load configuration with EC2-stored password and Role ARN $e8
  Parse valid load without credentials $e9
  """

  val configUrl = getClass.getResource("/valid-config.json")
  val resolverUrl = getClass.getResource("/resolver.json")

  val resolverBase64 = "eyJzY2hlbWEiOiJpZ2x1OmNvbS5zbm93cGxvd2FuYWx5dGljcy5pZ2x1L3Jlc29sdmVyLWNvbmZpZy9qc29uc2NoZW1hLzEtMC0xIiwiZGF0YSI6eyJjYWNoZVNpemUiOjUsInJlcG9zaXRvcmllcyI6W3sibmFtZSI6IklnbHUgQ2VudHJhbCBiYXNlNjQiLCJwcmlvcml0eSI6MCwidmVuZG9yUHJlZml4ZXMiOlsiY29tLnNub3dwbG93YW5hbHl0aWNzIl0sImNvbm5lY3Rpb24iOnsiaHR0cCI6eyJ1cmkiOiJodHRwOi8vaWdsdWNlbnRyYWwuY29tIn19fV19fQ=="
  // 127.0.0.1:8888
  // val resolverBase64 = "eyJzY2hlbWEiOiJpZ2x1OmNvbS5zbm93cGxvd2FuYWx5dGljcy5pZ2x1L3Jlc29sdmVyLWNvbmZpZy9qc29uc2NoZW1hLzEtMC0xIiwiZGF0YSI6eyJjYWNoZVNpemUiOjUsInJlcG9zaXRvcmllcyI6W3sibmFtZSI6IklnbHUgQ2VudHJhbCBiYXNlNjQiLCJwcmlvcml0eSI6MCwidmVuZG9yUHJlZml4ZXMiOlsiY29tLnNub3dwbG93YW5hbHl0aWNzIl0sImNvbm5lY3Rpb24iOnsiaHR0cCI6eyJ1cmkiOiJodHRwOi8vMTI3LjAuMC4xOjg4ODgifX19XX19"

  val roleConfigUrl = getClass.getResource("/valid-config-role.json")
  val secureConfigUrl = getClass.getResource("/valid-config-secure.json")
  val noauthConfigUrl = getClass.getResource("/valid-config-noauth.json")

  def e1 = {
    val args = List(
      "setup",

      "--resolver", s"${resolverUrl.getPath}",
      "--config", s"${configUrl.getPath}"
    ).toArray

    val expected = CliLoaderConfiguration(
      Config.SetupCommand,
      Config(
        auth = Config.CredentialsAuth(
          accessKeyId = "ABCD",
          secretAccessKey = "abcd"
        ),
        awsRegion = "us-east-1",
        manifest = "snowflake-manifest",
        stage = "some_stage",
        stageUrl = s3("s3://snowflake/output/"),
        snowflakeRegion = "us-west-1",
        username = "anton",
        password = Config.PlainText("Supersecret2"),
        input = s3("s3://snowflake/input/"),
        account = "snowplow",
        warehouse = "snowplow_wh",
        database = "test_db",
        schema = "atomic"),
      false)

    Config.parseLoaderCli(args) must beSome(Right(expected))
  }

  def e2 = {
    val args = List(
      "load",

      "--dry-run",
      "--resolver", s"${resolverUrl.getPath}",
      "--config", s"${configUrl.getPath}").toArray

    val expected = CliLoaderConfiguration(
      Config.LoadCommand,
      Config(
        auth = Config.CredentialsAuth(
          accessKeyId = "ABCD",
          secretAccessKey = "abcd"
        ),
        awsRegion = "us-east-1",
        manifest = "snowflake-manifest",
        stage = "some_stage",
        stageUrl = s3("s3://snowflake/output/"),
        snowflakeRegion = "us-west-1",
        input = s3("s3://snowflake/input/"),
        schema = "atomic",
        username = "anton",
        password = Config.PlainText("Supersecret2"),
        account = "snowplow",
        warehouse = "snowplow_wh",
        database = "test_db"),
      true)

    Config.parseLoaderCli(args) must beSome(Right(expected))
  }

  def e3 = {
    val args = List(
      "load",

      "--dry-run",
      "--base64",
      "--resolver", resolverBase64,
      "--config", "eyAic2NoZW1hIjogImlnbHU6Y29tLnNub3dwbG93YW5hbHl0aWNzLnNub3dwbG93LnN0b3JhZ2Uvc25vd2ZsYWtlX2NvbmZpZy9qc29uc2NoZW1hLzEtMC0wIiwgImRhdGEiOiB7ICJuYW1lIjogIlNub3dmbGFrZSBiYXNlNjQiLCAiYXV0aCI6IHsgImFjY2Vzc0tleUlkIjogIkFCQ0RBIiwgInNlY3JldEFjY2Vzc0tleSI6ICJhYmNkIiB9LCAiYXdzUmVnaW9uIjogInVzLWVhc3QtMSIsICJtYW5pZmVzdCI6ICJzbm93Zmxha2UtbWFuaWZlc3QiLCAic25vd2ZsYWtlUmVnaW9uIjogInVzLXdlc3QtMSIsICJkYXRhYmFzZSI6ICJ0ZXN0X2RiIiwgImlucHV0IjogInMzOi8vc25vd2ZsYWtlL2lucHV0LyIsICJzdGFnZSI6ICJzb21lX3N0YWdlIiwgInN0YWdlVXJsIjogInMzOi8vc25vd2ZsYWtlL291dHB1dC8iLCAid2FyZWhvdXNlIjogInNub3dwbG93X3doIiwgInNjaGVtYSI6ICJhdG9taWMiLCAiYWNjb3VudCI6ICJzbm93cGxvdyIsICJ1c2VybmFtZSI6ICJhbnRvbiIsICJwYXNzd29yZCI6ICJTdXBlcnNlY3JldDIiLCAicHVycG9zZSI6ICJFTlJJQ0hFRF9FVkVOVFMiIH0gfQ=="
    ).toArray

    val expected = CliLoaderConfiguration(
      Config.LoadCommand,
      Config(
        auth = Config.CredentialsAuth(
          accessKeyId = "ABCDA",
          secretAccessKey = "abcd"
        ),
        awsRegion = "us-east-1",
        manifest = "snowflake-manifest",
        stage = "some_stage",
        stageUrl = s3("s3://snowflake/output/"),
        snowflakeRegion = "us-west-1",
        schema = "atomic",
        username = "anton",
        password = Config.PlainText("Supersecret2"),
        input = s3("s3://snowflake/input/"),
        account = "snowplow",
        warehouse = "snowplow_wh",
        database = "test_db"),
      true)

    Config.parseLoaderCli(args) must beSome(Right(expected))

  }

  def e4 = {
    val result = Config.S3Folder.parse("s3://cross-batch-test/archive/some-folder")
    result must beRight(s3("s3://cross-batch-test/archive/some-folder/"))
  }

  def e5 = {
    val result = Config.S3Folder.parse("s3n://cross-batch-test/archive/some-folder/")
    result must beRight(s3("s3://cross-batch-test/archive/some-folder/"))
  }

  def e6 = {
    val result = Config.S3Folder.parse("http://cross-batch-test/archive/some-folder/")
    result must beLeft("Bucket name [http://cross-batch-test/archive/some-folder/] must start with s3:// prefix")
  }

  def e7 = {
    val args = List(
      "setup",

      "--resolver", resolverBase64,
      "--config", "eyAic2NoZW1hIjogImlnbHU6Y29tLnNub3dwbG93YW5hbHl0aWNzLnNub3dwbG93LnN0b3JhZ2Uvc25vd2ZsYWtlX2NvbmZpZy9qc29uc2NoZW1hLzEtMC0wIiwgImRhdGEiOiB7ICJuYW1lIjogIlNub3dmbGFrZSIsICJhdXRoIjogeyJyb2xlQXJuIjogImFybjphd3M6aWFtOjo3MTkxOTc0MzU5OTU6cm9sZS9Tbm93Zmxha2VSb2xlIiwgInNlc3Npb25EdXJhdGlvbiI6IDkwMH0sICJhd3NSZWdpb24iOiAidXMtZWFzdC0xIiwgIm1hbmlmZXN0IjogInNub3dmbGFrZS1tYW5pZmVzdCIsICJzbm93Zmxha2VSZWdpb24iOiAidXMtd2VzdC0xIiwgImRhdGFiYXNlIjogInRlc3RfZGIiLCAiaW5wdXQiOiAiczM6Ly9zbm93Zmxha2UvaW5wdXQvIiwgInN0YWdlIjogInNvbWVfc3RhZ2UiLCAic3RhZ2VVcmwiOiAiczM6Ly9zbm93Zmxha2Uvb3V0cHV0LyIsICJ3YXJlaG91c2UiOiAic25vd3Bsb3dfd2giLCAic2NoZW1hIjogImF0b21pYyIsICJhY2NvdW50IjogInNub3dwbG93IiwgInVzZXJuYW1lIjogImFudG9uIiwgInBhc3N3b3JkIjogIlN1cGVyc2VjcmV0MiIsICJwdXJwb3NlIjogIkVOUklDSEVEX0VWRU5UUyIgfSB9",
      "--base64"
    ).toArray

    val expected = CliLoaderConfiguration(
      Config.SetupCommand,
      Config(
        auth = Config.RoleAuth(
          roleArn = "arn:aws:iam::719197435995:role/SnowflakeRole",
          sessionDuration = 900
        ),
        awsRegion = "us-east-1",
        manifest = "snowflake-manifest",
        stage = "some_stage",
        stageUrl = s3("s3://snowflake/output/"),
        snowflakeRegion = "us-west-1",
        schema = "atomic",
        username = "anton",
        password = Config.PlainText("Supersecret2"),
        input = s3("s3://snowflake/input/"),
        account = "snowplow",
        warehouse = "snowplow_wh",
        database = "test_db"),
      false)

    Config.parseLoaderCli(args) must beSome(Right(expected))
  }

  def e8 = {
    val args = List(
      "load",

      "--dry-run",
      "--resolver", s"${resolverUrl.getPath}",
      "--config", s"${secureConfigUrl.getPath}").toArray

    val expected = CliLoaderConfiguration(
      Config.LoadCommand,
      Config(
        auth = Config.RoleAuth(
          roleArn = "arn:aws:iam::111222333444:role/SnowflakeLoadRole",
          sessionDuration = 900
        ),
        awsRegion = "us-east-1",
        manifest = "snowflake-manifest",
        stage = "some_stage",
        stageUrl = s3("s3://snowflake/output/"),
        snowflakeRegion = "us-west-1",
        input = s3("s3://snowflake/input/"),
        schema = "atomic",
        username = "anton",
        password = Config.EncryptedKey(
          Config.EncryptedConfig(
            Config.ParameterStoreConfig("snowplow.snowflakeloader.snowflake.password"))),
        account = "snowplow",
        warehouse = "snowplow_wh",
        database = "test_db"),
      true)

    Config.parseLoaderCli(args) must beSome(Right(expected))
  }

  def e9 = {
    val args = List(
      "load",

      "--dry-run",
      "--resolver", s"${resolverUrl.getPath}",
      "--config", s"${noauthConfigUrl.getPath}").toArray

    val expected = CliLoaderConfiguration(
      Config.LoadCommand,
      Config(
        auth = Config.StageAuth,
        awsRegion = "us-east-1",
        manifest = "snowflake-manifest",
        stage = "some_stage",
        stageUrl = s3("s3://snowflake/output/"),
        snowflakeRegion = "us-west-1",
        input = s3("s3://snowflake/input/"),
        schema = "atomic",
        username = "anton",
        password = Config.EncryptedKey(
          Config.EncryptedConfig(
            Config.ParameterStoreConfig("snowplow.snowflakeloader.snowflake.password"))),
        account = "snowplow",
        warehouse = "snowplow_wh",
        database = "test_db"),
      true)

    Config.parseLoaderCli(args) must beSome(Right(expected))
  }
}
