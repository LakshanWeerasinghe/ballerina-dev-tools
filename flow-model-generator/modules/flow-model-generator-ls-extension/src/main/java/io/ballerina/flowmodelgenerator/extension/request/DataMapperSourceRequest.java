/*
 *  Copyright (c) 2024, WSO2 LLC. (http://www.wso2.com)
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

package io.ballerina.flowmodelgenerator.extension.request;

import com.google.gson.JsonElement;
import io.ballerina.tools.text.LinePosition;

/**
 * Represents a request to get the data mapper model for types.
 *
 * @param filePath    file path of the source file
 * @param flowNode    flow node of form
 * @param mappings    data mappings
 * @param propertyKey The property that needs to consider to get the type
 * @since 1.4.0
 */
public record DataMapperSourceRequest(String filePath, JsonElement flowNode, JsonElement mappings,
                                      String propertyKey) {

}
