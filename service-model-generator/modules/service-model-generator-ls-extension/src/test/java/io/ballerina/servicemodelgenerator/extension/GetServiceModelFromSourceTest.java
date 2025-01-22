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

package io.ballerina.servicemodelgenerator.extension;

import com.google.gson.JsonObject;
import io.ballerina.servicemodelgenerator.extension.model.Codedata;
import io.ballerina.servicemodelgenerator.extension.model.Service;
import io.ballerina.servicemodelgenerator.extension.request.CommonModelFromSourceRequest;
import io.ballerina.servicemodelgenerator.extension.response.ServiceFromSourceResponse;
import io.ballerina.tools.text.LinePosition;
import io.ballerina.tools.text.LineRange;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assert the response returned by the getServiceFromSource.
 *
 * @since 2.0.0
 */
public class GetServiceModelFromSourceTest extends AbstractLSTest {

    @Override
    @Test(dataProvider = "data-provider")
    public void test(Path config) throws IOException {
        Path configJsonPath = configDir.resolve(config);
        GetServiceModelFromSourceTest.TestConfig testConfig = gson.fromJson(Files.newBufferedReader(configJsonPath),
                GetServiceModelFromSourceTest.TestConfig.class);

        String sourcePath = sourceDir.resolve(testConfig.filePath()).toAbsolutePath().toString();
        Codedata codedata = new Codedata(LineRange.from(sourcePath, testConfig.start(), testConfig.end()));
        CommonModelFromSourceRequest sourceRequest = new CommonModelFromSourceRequest(sourcePath, codedata);
        JsonObject jsonMap = getResponse(sourceRequest);
        ServiceFromSourceResponse serviceFromSourceResponse = gson.fromJson(jsonMap, ServiceFromSourceResponse.class);

        Service actualServiceModel = serviceFromSourceResponse.service();
        boolean assertTrue = isServiceEqual(actualServiceModel, testConfig.response());

        if (!assertTrue) {
            GetServiceModelFromSourceTest.TestConfig updatedConfig =
                    new GetServiceModelFromSourceTest.TestConfig(testConfig.filePath(), testConfig.description(),
                            testConfig.start(), testConfig.end(), actualServiceModel);
//            updateConfig(configJsonPath, updatedConfig);
            Assert.fail(String.format("Failed test: '%s' (%s)", testConfig.description(), configJsonPath));
        }
    }

    private static boolean isServiceEqual(Service actual, Service expected) {
        return expected.getId().equals(actual.getId()) &&
                expected.getName().equals(actual.getName()) &&
                expected.getType().equals(actual.getType()) &&
                expected.getModuleName().equals(actual.getModuleName()) &&
                expected.getOrgName().equals(actual.getOrgName()) &&
                expected.getListenerProtocol().equals(actual.getListenerProtocol()) &&
                expected.getFunctions().size() == actual.getFunctions().size() &&
                expected.getProperties().values().size() == actual.getProperties().values().size();
    }

    @Override
    protected String getResourceDir() {
        return "get_sm_from_source";
    }

    @Override
    protected Class<? extends AbstractLSTest> clazz() {
        return GetServiceModelFromSourceTest.class;
    }

    @Override
    protected String getApiName() {
        return "getServiceFromSource";
    }

    /**
     * Represents the test configuration for the source generator test.
     *
     * @param filePath    The path to the source file
     * @param description The description of the test
     * @param start      The start position of the service declaration node
     * @param end       The end position of the service declaration node
     * @param response    The expected response
     */
    private record TestConfig(String filePath, String description, LinePosition start, LinePosition end,
                              Service response) {
        public String description() {
            return description == null ? "" : description;
        }
    }
}
