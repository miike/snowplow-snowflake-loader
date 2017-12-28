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
package com.snowplowanalytics.snowflake.loader.ast

import cats.implicits._

trait Statement[-S] {
  def getStatement(ast: S): Statement.SqlStatement
}

object Statement {

  final case class SqlStatement private(value: String) extends AnyVal

  implicit object CreateTableStatement extends Statement[CreateTable] {
    def getStatement(ddl: CreateTable): SqlStatement = {
      val constraint = ddl.primaryKey.map { p => ", " + p.show }.getOrElse("")
      val cols = ddl.columns.map(_.show).map(_.split(" ").toList).map {
        case columnName :: tail => columnName + " " + tail.mkString(" ")
        case other => other.mkString(" ")
      }
      val temporary = if (ddl.temporary) " TEMPORARY " else " "
      SqlStatement(s"CREATE${temporary}TABLE IF NOT EXISTS ${ddl.schema}.${ddl.name} (" +
        cols.mkString(", ") + constraint + ")"
      )
    }
  }

  implicit object CreateSchemaStatement extends Statement[CreateSchema] {
    def getStatement(ddl: CreateSchema): SqlStatement =
      SqlStatement(s"CREATE SCHEMA IF NOT EXISTS ${ddl.name}")
  }

  implicit object CreateFileFormatStatement extends Statement[CreateFileFormat] {
    def getStatement(ddl: CreateFileFormat): SqlStatement = ddl match {
      case CreateFileFormat.CreateCsvFormat(name, recordDelimiter, fieldDelimiter) =>
        val recordDelRendered = s"RECORD_DELIMITER = '${recordDelimiter.getOrElse("NONE")}'"
        val fieldDelRendered = s"FIELD_DELIMITER = '${fieldDelimiter.getOrElse("NONE")}'"
        SqlStatement(s"CREATE FILE FORMAT IF NOT EXISTS $name TYPE = CSV $recordDelRendered $fieldDelRendered")
      case CreateFileFormat.CreateJsonFormat(name) =>
        SqlStatement(s"CREATE FILE FORMAT IF NOT EXISTS $name TYPE = JSON")
    }
  }

  implicit object CreateStageStatement extends Statement[CreateStage] {
    def getStatement(ddl: CreateStage): SqlStatement = {
      val credentials = ddl.credentials match {
        case Some(c) if c.sessionToken.isDefined =>
          System.err.println("AWS_TOKEN (temporary credentials) must never be used for stages. Skipping credentials")
          None
        case Some(c) => s" CREDENTIALS = (AWS_KEY_ID = '${c.awsAccessKeyId}' AWS_SECRET_KEY = '${c.awsSecretKey}')"
        case None => ""  // Expect credentials are available in stage
      }
      SqlStatement(
        s"CREATE STAGE IF NOT EXISTS ${ddl.schema}.${ddl.name} URL = '${ddl.url}' FILE_FORMAT = ${ddl.fileFormat}$credentials"
      )
    }
  }

  implicit object CreateWarehouseStatement extends Statement[CreateWarehouse] {
    def getStatement(ddl: CreateWarehouse): SqlStatement = {
      val size = ddl.size.getOrElse(CreateWarehouse.DefaultSize).toString.toUpperCase
      SqlStatement(s"CREATE WAREHOUSE IF NOT EXISTS ${ddl.name} WAREHOUSE_SIZE = $size")
    }
  }

  implicit object SelectStatement extends Statement[Select] {
    def getStatement(ddl: Select): SqlStatement =
      SqlStatement(s"SELECT ${ddl.columns.map(_.show).mkString(", ")} FROM ${ddl.schema}.${ddl.table}")
  }

  implicit object InsertStatement extends Statement[Insert] {
    def getStatement(ddl: Insert): SqlStatement = ddl match {
      case Insert.InsertQuery(schema, table, columns, from) =>
        SqlStatement(s"INSERT INTO $schema.$table(${columns.mkString(",")}) ${from.getStatement.value}")
    }
  }

  implicit object AlterTableStatement extends Statement[AlterTable] {
    def getStatement(ast: AlterTable): SqlStatement = ast match {
      case AlterTable.AddColumn(schema, table, column, datatype) =>
        SqlStatement(s"ALTER TABLE $schema.$table ADD COLUMN $column ${datatype.show}")
      case AlterTable.DropColumn(schema, table, column) =>
        SqlStatement(s"ALTER TABLE $schema.$table DROP COLUMN $column")
    }
  }

  implicit object AlterWarehouseStatement extends Statement[AlterWarehouse] {
    def getStatement(ast: AlterWarehouse): SqlStatement = ast match {
      case AlterWarehouse.Resume(warehouse) =>
        SqlStatement(s"ALTER WAREHOUSE $warehouse RESUME")
    }
  }

  implicit object UseWarehouseStatement extends Statement[UseWarehouse] {
    def getStatement(ast: UseWarehouse): SqlStatement =
      SqlStatement(s"USE WAREHOUSE ${ast.warehouse}")
  }

  implicit object CopyInto extends Statement[CopyInto] {
    def getStatement(ast: CopyInto): SqlStatement = {
      val credentials = ast.credentials match {
        case Some(c) =>
          val token = c.sessionToken match {
            case None => ""
            case Some(t) => s" AWS_TOKEN = '$t'"
          }
          s" CREDENTIALS = (AWS_KEY_ID = '${c.awsAccessKeyId}' AWS_SECRET_KEY = '${c.awsSecretKey}'$token)"
        case None => ""  // Expect credentials are available in stage
      }
      val stripNulls = if (ast.stripNullValues) " STRIP_NULL_VALUES = TRUE"
      else ""
      SqlStatement(s"COPY INTO ${ast.schema}.${ast.table}(${ast.columns.mkString(",")}) FROM @${ast.from.schema}.${ast.from.stageName}/${ast.from.path}$credentials FILE_FORMAT = (FORMAT_NAME = '${ast.fileFormat.schema}.${ast.fileFormat.formatName}'$stripNulls)" )

    }
  }

  implicit object ShowStageStatement extends Statement[Show.ShowStages] {
    def getStatement(ast: Show.ShowStages): SqlStatement = {
      val schemaPattern = ast.pattern.map(s => s" LIKE '$s'").getOrElse("")
      val scopePattern = ast.schema.map(s => s" IN $s").getOrElse("")
      SqlStatement(s"SHOW stages$schemaPattern$scopePattern")
    }
  }

  implicit object ShowSchemasStatement extends Statement[Show.ShowSchemas] {
    def getStatement(ast: Show.ShowSchemas): SqlStatement = {
      val schemaPattern = ast.pattern.map(s => s" LIKE '$s'").getOrElse("")
      SqlStatement(s"SHOW schemas$schemaPattern")
    }
  }

  implicit object ShowTablesStatement extends Statement[Show.ShowTables] {
    def getStatement(ast: Show.ShowTables): SqlStatement = {
      val schemaPattern = ast.pattern.map(s => s" LIKE '$s'").getOrElse("")
      val scopePattern = ast.schema.map(s => s" IN $s").getOrElse("")
      SqlStatement(s"SHOW tables$schemaPattern$scopePattern")
    }
  }

  implicit object ShowFileFormatsStatement extends Statement[Show.ShowFileFormats] {
    def getStatement(ast: Show.ShowFileFormats): SqlStatement = {
      val schemaPattern = ast.pattern.map(s => s" LIKE '$s'").getOrElse("")
      val scopePattern = ast.schema.map(s => s" IN $s").getOrElse("")
      SqlStatement(s"SHOW file formats$schemaPattern$scopePattern")
    }
  }

  implicit object ShowWarehousesStatement extends Statement[Show.ShowWarehouses] {
    def getStatement(ast: Show.ShowWarehouses): SqlStatement = {
      val schemaPattern = ast.pattern.map(s => s" LIKE '$s'").getOrElse("")
      SqlStatement(s"SHOW warehouses$schemaPattern")
    }
  }
}
