/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com)
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.ballerina.testmanagerservice.extension;

import com.google.gson.JsonObject;
import io.ballerina.testmanagerservice.extension.request.TestsDiscoveryRequest;
import io.ballerina.testmanagerservice.extension.response.ProjectTestsDiscoveryResponse;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assert the response returned by the discoverInProject.
 *
 * @since 2.0.0
 */
public class TestProjectTestDiscovery extends AbstractLSTest {

    @Override
    @Test(dataProvider = "data-provider")
    public void test(Path config) throws IOException {
        Path configJsonPath = configDir.resolve(config);
        TestProjectTestDiscovery.TestConfig testConfig = gson.fromJson(Files.newBufferedReader(configJsonPath),
                TestProjectTestDiscovery.TestConfig.class);

        String testSourcePath = sourceDir.resolve(testConfig.filePath()).toAbsolutePath().toString();
        TestsDiscoveryRequest request = new TestsDiscoveryRequest(testSourcePath);
        JsonObject jsonMap = getResponse(request);

        ProjectTestsDiscoveryResponse testsDiscoveryResponse = gson.fromJson(jsonMap, ProjectTestsDiscoveryResponse.class);
        boolean assertTrue = false;

        if (!assertTrue) {
            TestProjectTestDiscovery.TestConfig updatedConfig =
                    new TestProjectTestDiscovery.TestConfig(testConfig.filePath(), testConfig.description(),
                            testsDiscoveryResponse);
            updateConfig(configJsonPath, updatedConfig);
            Assert.fail(String.format("Failed test: '%s' (%s)", testConfig.description(), configJsonPath));
        }
    }

    @Override
    protected String getResourceDir() {
        return "discover_in_project";
    }

    @Override
    protected Class<? extends AbstractLSTest> clazz() {
        return TestProjectTestDiscovery.class;
    }

    @Override
    protected String getApiName() {
        return "discoverInProject";
    }

    /**
     * Represents the test configuration.
     *
     * @param description The description of the test
     * @param response    The expected response
     */
    private record TestConfig(String filePath, String description, ProjectTestsDiscoveryResponse response) {
        public String description() {
            return description == null ? "" : description;
        }
    }
}
