package org.apache.zest.tools.shell.create.project.restapp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

public class SerializationModuleWriter
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
                "import java.util.function.Function;\n" +
                "import org.apache.zest.api.common.Visibility;\n" +
                "import org.apache.zest.api.structure.Application;\n" +
                "import org.apache.zest.api.structure.Module;\n" +
                "import org.apache.zest.bootstrap.AssemblyException;\n" +
                "import org.apache.zest.bootstrap.LayerAssembly;\n" +
                "import org.apache.zest.bootstrap.ModuleAssembly;\n" +
                "import org.apache.zest.bootstrap.layered.ModuleAssembler;\n" +
                "import org.apache.zest.spi.uuid.UuidIdentityGeneratorService;\n" +
                "import org.apache.zest.valueserialization.jackson.JacksonValueSerializationAssembler;\n" +
                "\n" +
                "public class SerializationModule\n" +
                "    implements ModuleAssembler\n" +
                "{\n" +
                "    public static final String NAME = \"Serialization Module\";\n" +
                "    private final Function<Application, Module> typeFinder;\n" +
                "\n" +
                "    public SerializationModule( Function<Application, Module> typeFinder )\n" +
                "    {\n" +
                "        this.typeFinder = typeFinder;\n" +
                "    }\n" +
                "\n" +
                "    @Override\n" +
                "    public ModuleAssembly assemble( LayerAssembly layer, ModuleAssembly module )\n" +
                "        throws AssemblyException\n" +
                "    {\n" +
                "        new JacksonValueSerializationAssembler()\n" +
                "            .visibleIn( Visibility.application )\n" +
                "            .withValuesModuleFinder( typeFinder )\n" +
                "            .assemble( module );\n" +
                "        module.services( UuidIdentityGeneratorService.class ).visibleIn( Visibility.layer );\n" +
                "        return module;\n" +
                "    }\n" +
                "}\n"
            );
        }
    }

    private PrintWriter createPrinter( Map<String, String> properties )
        throws IOException
    {
        String packagename = properties.get( "root.package" ).replaceAll( "\\.", "/" ) + "/bootstrap/infrastructure/";
        String classname = "SerializationModule";
        File projectDir = new File( properties.get( "project.dir" ) );
        return new PrintWriter( new FileWriter( new File( projectDir, "bootstrap/src/main/java/" + packagename + classname + ".java" ) ) );
    }
}
