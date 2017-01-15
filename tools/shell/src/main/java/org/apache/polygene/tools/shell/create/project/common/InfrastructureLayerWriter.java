/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package org.apache.polygene.tools.shell.create.project.common;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import org.apache.polygene.tools.shell.FileUtils;

public class InfrastructureLayerWriter
{

    public void writeClass( Map<String, String> properties )
        throws IOException
    {
        String rootPackage = properties.get( "root.package" );
        String projectName = properties.get( "project.name" );
        try (PrintWriter pw = createPrinter( properties ))
        {
            pw.print( "package " );
            pw.print( properties.get( "root.package" ) );
            pw.println( ".bootstrap.infrastructure;" );
            pw.println();
            pw.println(
                "import org.apache.polygene.bootstrap.AssemblyException;\n" +
                "import org.apache.polygene.bootstrap.LayerAssembly;\n" +
                "import org.apache.polygene.bootstrap.ModuleAssembly;\n" +
                "import org.apache.polygene.bootstrap.layered.LayerAssembler;\n" +
                "import org.apache.polygene.bootstrap.layered.LayeredLayerAssembler;\n" +
                "\n" +
                "public class InfrastructureLayer extends LayeredLayerAssembler\n" +
                "    implements LayerAssembler\n" +
                "{\n" +
                "    public static final String NAME = \"Infrastructure Layer\";\n" +
                "    private final ModuleAssembly configModule;\n" +
                "\n" +
                "    public InfrastructureLayer( ModuleAssembly configModule )\n" +
                "    {\n" +
                "        this.configModule = configModule;\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    public LayerAssembly assemble( LayerAssembly layer )\n" +
                "        throws AssemblyException\n" +
                "    {\n" +
                "        createModule( layer, FileConfigurationModule.class );\n" +
                "\n" +
                "        new StorageModule( configModule ).assemble( layer, layer.module( StorageModule.NAME ) );\n" +
                "        new IndexingModule( configModule ).assemble( layer, layer.module( IndexingModule.NAME ) );\n" +
                "        new SerializationModule().assemble( layer, layer.module( SerializationModule.NAME ) );\n" +
                "\n" +
                "        return layer;\n" +
                "    }\n" +
                "}\n"
            );
        }
    }

    private PrintWriter createPrinter( Map<String, String> properties )
        throws IOException
    {
        String packagename = properties.get( "root.package" ).replaceAll( "\\.", "/" ) + "/bootstrap/infrastructure/";
        String classname = "InfrastructureLayer";
        return FileUtils.createJavaClassPrintWriter( properties, "bootstrap", packagename, classname );
    }
}
