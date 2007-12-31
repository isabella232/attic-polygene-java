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

package org.qi4j.runtime.structure;

import java.util.ArrayList;
import java.util.List;
import org.qi4j.spi.structure.LayerBinding;

/**
 * TODO
 */
public final class LayerContext
{
    LayerBinding layerBinding;
    Iterable<ModuleContext> moduleContexts;

    public LayerContext( LayerBinding layerBinding, Iterable<ModuleContext> moduleContexts )
    {
        this.layerBinding = layerBinding;
        this.moduleContexts = moduleContexts;
    }

    public LayerBinding getLayerBinding()
    {
        return layerBinding;
    }

    LayerInstance newLayerInstance()
    {
        List<ModuleInstance> moduleInstances = new ArrayList<ModuleInstance>();
        for( ModuleContext moduleContext : moduleContexts )
        {
            ModuleInstance moduleInstance = moduleContext.newModuleInstance();
            moduleInstances.add( moduleInstance );
        }

        return new LayerInstance( this, moduleInstances );
    }

}
