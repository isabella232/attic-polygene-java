/*  Copyright 2008 Edward Yakop.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
* implied.
*
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.qi4j.library.swing.visualizer.detailPanel.internal.tree;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import org.qi4j.library.swing.visualizer.model.ApplicationDetailDescriptor;
import org.qi4j.library.swing.visualizer.model.CompositeDetailDescriptor;
import org.qi4j.library.swing.visualizer.model.EntityDetailDescriptor;
import org.qi4j.library.swing.visualizer.model.LayerDetailDescriptor;
import org.qi4j.library.swing.visualizer.model.ModuleDetailDescriptor;
import org.qi4j.library.swing.visualizer.model.ObjectDetailDescriptor;
import org.qi4j.library.swing.visualizer.model.ServiceDetailDescriptor;
import org.qi4j.service.ServiceDescriptor;
import org.qi4j.spi.composite.CompositeDescriptor;
import org.qi4j.spi.entity.EntityDescriptor;
import org.qi4j.spi.object.ObjectDescriptor;
import org.qi4j.structure.Visibility;
import static org.qi4j.structure.Visibility.application;

/**
 * @author edward.yakop@gmail.com
 * @since 0.5
 */
public final class TreeModelBuilder
{
    private boolean isDisplayApplicationScopeItems;
    private boolean isDisplayLayerScopeItems;

    public TreeModelBuilder()
    {
        isDisplayApplicationScopeItems = false;
        isDisplayLayerScopeItems = false;
    }

    public final void displayApplicationScopeItems( boolean isEnabled )
    {
        isDisplayApplicationScopeItems = isEnabled;
    }

    public final boolean isDisplayApplicationScopeItems()
    {
        return isDisplayApplicationScopeItems;
    }

    public final void displayLayerScopeItems( boolean isEnabled )
    {
        isDisplayLayerScopeItems = isEnabled;
    }

    public final boolean isDisplayLayerScopeItems()
    {
        return isDisplayLayerScopeItems;
    }

    public final DefaultMutableTreeNode populate( ApplicationDetailDescriptor aDetailDescriptor )
    {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode( aDetailDescriptor );

        if( aDetailDescriptor != null )
        {
            addLayersNode( root, aDetailDescriptor );
            addApplicationScopeItemsNodes( root, aDetailDescriptor );
        }

        return root;
    }

    private void addLayersNode( DefaultMutableTreeNode root, ApplicationDetailDescriptor aDetailDescriptor )
    {
        DefaultMutableTreeNode layersNode = new DefaultMutableTreeNode( "layers" );

        Iterable<LayerDetailDescriptor> layers = aDetailDescriptor.layers();
        for( LayerDetailDescriptor layer : layers )
        {
            DefaultMutableTreeNode layerNode = new DefaultMutableTreeNode( layer );
            layersNode.add( layerNode );

            addModulesNode( layerNode, layer );
            addLayerScopeItemNodes( layerNode, layer );
        }

        addIfNotEmpty( root, layersNode );
    }

    private void addIfNotEmpty( DefaultMutableTreeNode parent, MutableTreeNode children )
    {
        int numberOfChildren = children.getChildCount();
        if( numberOfChildren > 0 )
        {
            parent.add( children );
        }
    }

    private void addModulesNode( DefaultMutableTreeNode layersNode, LayerDetailDescriptor aLayer )
    {
        DefaultMutableTreeNode modulesNode = new DefaultMutableTreeNode( "modules" );
        layersNode.add( modulesNode );

        Iterable<ModuleDetailDescriptor> modules = aLayer.modules();
        for( ModuleDetailDescriptor module : modules )
        {
            addModuleNode( modulesNode, module );
        }
    }

    private void addModuleNode( DefaultMutableTreeNode modulesNode, ModuleDetailDescriptor aModule )
    {
        String moduleName = aModule.descriptor().name();
        DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode( moduleName );
        modulesNode.add( moduleNode );

        DefaultMutableTreeNode servicesNode = new DefaultMutableTreeNode( "services" );
        addServiceNodes( servicesNode, aModule, null );
        addIfNotEmpty( moduleNode, servicesNode );

        DefaultMutableTreeNode entities = new DefaultMutableTreeNode( "entities" );
        addEntityNodes( entities, aModule, null );
        addIfNotEmpty( moduleNode, entities );

        DefaultMutableTreeNode composites = new DefaultMutableTreeNode( "composites" );
        addCompositeNodes( composites, aModule, null );
        addIfNotEmpty( moduleNode, composites );

        DefaultMutableTreeNode objects = new DefaultMutableTreeNode( "objects" );
        addObjectNodes( objects, aModule, null );
        addIfNotEmpty( moduleNode, objects );
    }

    private void addServiceNodes(
        DefaultMutableTreeNode aServicesNode,
        ModuleDetailDescriptor aModule,
        Visibility aVisibilityFilter )
    {
        Iterable<ServiceDetailDescriptor> services = aModule.services();
        for( ServiceDetailDescriptor service : services )
        {
            ServiceDescriptor descriptor = service.descriptor();
            Visibility visibility = descriptor.visibility();
            if( aVisibilityFilter == null || visibility == aVisibilityFilter )
            {
                aServicesNode.add( new DefaultMutableTreeNode( service ) );
            }
        }
    }

    private void addEntityNodes(
        DefaultMutableTreeNode aEntitiesNode,
        ModuleDetailDescriptor aModule,
        Visibility aVisibilityFilter )
    {
        Iterable<EntityDetailDescriptor> entities = aModule.entities();
        for( EntityDetailDescriptor entity : entities )
        {
            EntityDescriptor entityDesc = entity.descriptor();
            Visibility visibility = entityDesc.visibility();
            if( aVisibilityFilter == null || visibility == aVisibilityFilter )
            {
                aEntitiesNode.add( new DefaultMutableTreeNode( entity ) );
            }
        }
    }

    private void addCompositeNodes(
        DefaultMutableTreeNode aCompositesNode,
        ModuleDetailDescriptor aModule,
        Visibility aVisibilityFilter )
    {
        Iterable<CompositeDetailDescriptor> composites = aModule.composites();
        for( CompositeDetailDescriptor composite : composites )
        {
            CompositeDescriptor compDesc = composite.descriptor();
            Visibility visibility = compDesc.visibility();
            if( aVisibilityFilter == null || visibility == aVisibilityFilter )
            {
                aCompositesNode.add( new DefaultMutableTreeNode( composite ) );
            }
        }
    }

    private void addObjectNodes(
        DefaultMutableTreeNode aObjectsNode,
        ModuleDetailDescriptor aModule,
        Visibility aVisibilityFilter )
    {
        Iterable<ObjectDetailDescriptor> objects = aModule.objects();
        for( ObjectDetailDescriptor object : objects )
        {
            ObjectDescriptor descriptor = object.descriptor();
            Visibility visibility = descriptor.visibility();
            if( aVisibilityFilter == null || visibility == aVisibilityFilter )
            {
                aObjectsNode.add( new DefaultMutableTreeNode( object ) );
            }
        }
    }


    private void addLayerScopeItemNodes( DefaultMutableTreeNode layersNode, LayerDetailDescriptor layer )
    {
        if( !isDisplayLayerScopeItems )
        {
            return;
        }

        DefaultMutableTreeNode servicesNode = new DefaultMutableTreeNode( "services" );
        DefaultMutableTreeNode entities = new DefaultMutableTreeNode( "entities" );
        DefaultMutableTreeNode composites = new DefaultMutableTreeNode( "composites" );
        DefaultMutableTreeNode objects = new DefaultMutableTreeNode( "objects" );

        Iterable<ModuleDetailDescriptor> modules = layer.modules();
        for( ModuleDetailDescriptor module : modules )
        {
            addServiceNodes( servicesNode, module, application );
            addEntityNodes( entities, module, application );
            addCompositeNodes( composites, module, application );
            addObjectNodes( objects, module, application );
        }

        addIfNotEmpty( layersNode, servicesNode );
        addIfNotEmpty( layersNode, entities );
        addIfNotEmpty( layersNode, composites );
        addIfNotEmpty( layersNode, objects );
    }

    private void addApplicationScopeItemsNodes(
        DefaultMutableTreeNode root,
        ApplicationDetailDescriptor aDetailDescriptor )
    {
        if( !isDisplayApplicationScopeItems )
        {
            return;
        }

        DefaultMutableTreeNode servicesNode = new DefaultMutableTreeNode( "services" );
        DefaultMutableTreeNode entities = new DefaultMutableTreeNode( "entities" );
        DefaultMutableTreeNode composites = new DefaultMutableTreeNode( "composites" );
        DefaultMutableTreeNode objects = new DefaultMutableTreeNode( "objects" );

        Iterable<LayerDetailDescriptor> layers = aDetailDescriptor.layers();
        for( LayerDetailDescriptor layer : layers )
        {
            Iterable<ModuleDetailDescriptor> modules = layer.modules();
            for( ModuleDetailDescriptor module : modules )
            {
                addServiceNodes( servicesNode, module, application );
                addEntityNodes( entities, module, application );
                addCompositeNodes( composites, module, application );
                addObjectNodes( objects, module, application );
            }
        }

        addIfNotEmpty( root, servicesNode );
        addIfNotEmpty( root, entities );
        addIfNotEmpty( root, composites );
        addIfNotEmpty( root, objects );
    }
}
