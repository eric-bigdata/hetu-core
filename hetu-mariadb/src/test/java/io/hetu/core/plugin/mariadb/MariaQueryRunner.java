/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.hetu.core.plugin.mariadb;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.testing.mysql.TestingMySqlServer;
import io.airlift.tpch.TpchTable;
import io.prestosql.Session;
import io.prestosql.plugin.tpch.TpchPlugin;
import io.prestosql.testing.QueryRunner;
import io.prestosql.tests.DistributedQueryRunner;

import java.util.HashMap;
import java.util.Map;

import static io.airlift.testing.Closeables.closeAllSuppress;
import static io.prestosql.plugin.tpch.TpchMetadata.TINY_SCHEMA_NAME;
import static io.prestosql.testing.TestingSession.testSessionBuilder;
import static io.prestosql.tests.QueryAssertions.copyTpchTables;

public final class MariaQueryRunner
{
    private MariaQueryRunner()
    {
    }

    private static final String TPCH_SCHEMA = "tpch";

    public static final TestingMySqlServer createTestingMariaServer(String userName, String password, String schemaName)
            throws Exception
    {
        return createTestingMariaServer(userName, password, schemaName, null);
    }

    public static final TestingMySqlServer createTestingMariaServer(String userName, String password, String schemaName, String dataBase)
            throws Exception
    {
        TestingMySqlServer tempServer = null;
        final int createRetry = 3;
        for (int i = 0; i < createRetry; i++) {
            try {
                if (dataBase == null) {
                    tempServer = new TestingMySqlServer(userName, password, schemaName);
                }
                else {
                    tempServer = new TestingMySqlServer(userName, password, schemaName, dataBase);
                }
            }
            catch (Exception e) {
                if (i == (createRetry - 1)) {
                    throw e;
                }
                continue;
            }
            break;
        }

        return tempServer;
    }

    public static QueryRunner createMariaQueryRunner(TestingMySqlServer server, TpchTable<?>... tables)
            throws Exception
    {
        return createMariaQueryRunner(server, ImmutableMap.of(), ImmutableList.copyOf(tables));
    }

    public static QueryRunner createMariaQueryRunner(TestingMySqlServer server, Map<String, String> connectorProperties, Iterable<TpchTable<?>> tables)
            throws Exception
    {
        try {
            return createMariaQueryRunner(server.getJdbcUrl(), connectorProperties, tables);
        }
        catch (Throwable e) {
            closeAllSuppress(e, server);
            throw e;
        }
    }

    public static QueryRunner createMariaQueryRunner(String jdbcUrl, Map<String, String> connectorPropertiesMap, Iterable<TpchTable<?>> tables)
            throws Exception
    {
        Map<String, String> connectorProperties = connectorPropertiesMap;
        DistributedQueryRunner queryRunner = null;
        try {
            queryRunner = new DistributedQueryRunner(createSession(), 3);

            queryRunner.installPlugin(new TpchPlugin());
            queryRunner.createCatalog("tpch", "tpch");

            connectorProperties = new HashMap<>(ImmutableMap.copyOf(connectorProperties));
            connectorProperties.putIfAbsent("connection-url", jdbcUrl);
            connectorProperties.putIfAbsent("allow-drop-table", "true");

            queryRunner.installPlugin(new MariaPlugin());
            queryRunner.createCatalog("maria", "maria", connectorProperties);

            copyTpchTables(queryRunner, "tpch", TINY_SCHEMA_NAME, createSession(), tables);

            return queryRunner;
        }
        catch (Throwable e) {
            closeAllSuppress(e, queryRunner);
            throw e;
        }
    }

    public static Session createSession()
    {
        return testSessionBuilder().setCatalog("maria").setSchema(TPCH_SCHEMA).build();
    }
}