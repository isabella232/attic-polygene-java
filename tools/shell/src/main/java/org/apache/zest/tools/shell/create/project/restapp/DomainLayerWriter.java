package org.apache.zest.tools.shell.create.project.restapp;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

public class DomainLayerWriter
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
            pw.println( ".bootstrap.domain;" );
            pw.println();
            pw.println(
                "import java.util.function.Function;\n" +
                "import org.apache.zest.api.structure.Application;\n" +
                "import org.apache.zest.api.structure.Module;\n" +
                "import org.apache.zest.bootstrap.AssemblyException;\n" +
                "import org.apache.zest.bootstrap.LayerAssembly;\n" +
                "import org.apache.zest.bootstrap.layered.LayerAssembler;\n" +
                "import org.apache.zest.bootstrap.layered.LayeredLayerAssembler;\n" +
                "\n" +
                "public class DomainLayer extends LayeredLayerAssembler\n" +
                "    implements LayerAssembler\n" +
                "{\n" +
                "    @Override\n" +
                "    public LayerAssembly assemble(LayerAssembly layer)\n" +
                "        throws AssemblyException\n" +
                "    {\n" +
                "        createModule( layer, CrudModule.class );\n" +
                "        createModule( layer, OrderModule.class );   // This is a simple sample that you typically remove.\n" +
                "        return layer;\n" +
                "    }\n" +
                "\n" +
                "    public static Function<Application, Module> typeFinder()\n" +
                "    {\n" +
                "        return application -> application.findModule( \"Domain Layer\", \"Assets Module\" );\n" +
                "    }\n" +
                "}\n"
            );
        }
    }

    private PrintWriter createPrinter( Map<String, String> properties )
        throws IOException
    {
        String packagename = properties.get( "root.package" ).replaceAll( "\\.", "/" ) + "/bootstrap/domain/";
        String classname = "DomainLayer";
        File projectDir = new File( properties.get( "project.dir" ) );
        return new PrintWriter( new FileWriter( new File( projectDir, "bootstrap/src/main/java/" + packagename + classname + ".java" ) ) );
    }
}
