/*
 * Copyright (c) 2007, Rickard Öberg. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.qi4j.bootstrap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.qi4j.composite.Composite;
import org.qi4j.spi.service.ServiceProvider;

/**
 * TODO
 */
public final class ModuleAssembly
{
    private LayerAssembly layerAssembly;
    private Set<Class> objects = new LinkedHashSet<Class>();
    private Map<Class, ServiceProvider> serviceProviders = new HashMap<Class, ServiceProvider>();
    private String name;
    private List<CompositeDeclaration> compositeDeclarations = new ArrayList<CompositeDeclaration>();
    private List<ObjectDeclaration> objectDeclarations = new ArrayList<ObjectDeclaration>();
    private List<PropertyDeclaration> propertyDeclarations = new ArrayList<PropertyDeclaration>();
    private List<AssociationBuilder> associationBuilders = new ArrayList<AssociationBuilder>();

    public ModuleAssembly( LayerAssembly layerAssembly )
    {
        this.layerAssembly = layerAssembly;
    }

    public void addAssembly( Assembly assembly )
        throws AssemblyException
    {
        // Invoke Assembly callbacks
        assembly.configure( this );
    }

    public LayerAssembly getLayerAssembly()
    {
        return layerAssembly;
    }

    public void setName( String name )
    {
        this.name = name;
    }

    public CompositeDeclaration addComposites( Class<? extends Composite>... compositeTypes )
    {
        CompositeDeclaration compositeDeclaration = new CompositeDeclaration( compositeTypes );
        compositeDeclarations.add( compositeDeclaration );
        return compositeDeclaration;
    }

    public ObjectDeclaration addObjects( Class... objectTypes )
    {
        ObjectDeclaration objectDeclaration = new ObjectDeclaration( Arrays.asList( objectTypes ) );
        objectDeclarations.add( objectDeclaration );
        return objectDeclaration;
    }

    public void addServiceProvider( ServiceProvider serviceProvider, Class... serviceTypes )
    {
        for( Class serviceType : serviceTypes )
        {
            serviceProviders.put( serviceType, serviceProvider );

            if( Composite.class.isAssignableFrom( serviceType ) )
            {
                addComposites( (Class<? extends Composite>) serviceType );
            }
        }

        addObjects( serviceProvider.getClass() );
    }

    public PropertyDeclaration addProperty()
    {
        PropertyDeclaration declaration = new PropertyDeclaration();
        propertyDeclarations.add( declaration );
        return declaration;
    }

    public AssociationBuilder addAssociation()
    {
        AssociationBuilder builder = new AssociationBuilder();
        associationBuilders.add( builder );
        return builder;
    }

    List<CompositeDeclaration> getCompositeDeclarations()
    {
        return compositeDeclarations;
    }

    List<ObjectDeclaration> getObjectDeclarations()
    {
        return objectDeclarations;
    }

    Set<Class<? extends Composite>> getPublicComposites()
    {
        Set<Class<? extends Composite>> publicComposites = new HashSet<Class<? extends Composite>>();
        for( CompositeDeclaration compositeDeclaration : compositeDeclarations )
        {
            if( compositeDeclaration.getModulePublic() )
            {
                for( Class<? extends Composite> compositeType : compositeDeclaration.getCompositeTypes() )
                {
                    publicComposites.add( compositeType );
                }
            }
        }
        return publicComposites;
    }

    Set<Class<? extends Composite>> getPrivateComposites()
    {
        Set<Class<? extends Composite>> privateComposites = new HashSet<Class<? extends Composite>>();
        for( CompositeDeclaration compositeDeclaration : compositeDeclarations )
        {
            if( !compositeDeclaration.getModulePublic() )
            {
                for( Class<? extends Composite> compositeType : compositeDeclaration.getCompositeTypes() )
                {
                    privateComposites.add( compositeType );
                }
            }
        }
        return privateComposites;
    }

    public Set<Class> getObjects()
    {
        return objects;
    }

    Map<Class, ServiceProvider> getServiceProviders()
    {
        return serviceProviders;
    }

    List<PropertyDeclaration> getPropertyBuilders()
    {
        return propertyDeclarations;
    }

    public List<AssociationBuilder> getAssociationBuilders()
    {
        return associationBuilders;
    }

    String getName()
    {
        return name;
    }
}
