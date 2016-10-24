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

package org.apache.zest.runtime.unitofwork;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import org.apache.zest.api.common.MetaInfo;
import org.apache.zest.api.entity.EntityComposite;
import org.apache.zest.api.entity.EntityDescriptor;
import org.apache.zest.api.entity.EntityReference;
import org.apache.zest.api.metrics.MetricsCounter;
import org.apache.zest.api.metrics.MetricsCounterFactory;
import org.apache.zest.api.metrics.MetricsProvider;
import org.apache.zest.api.metrics.MetricsTimer;
import org.apache.zest.api.metrics.MetricsTimerFactory;
import org.apache.zest.api.structure.ModuleDescriptor;
import org.apache.zest.api.type.HasTypes;
import org.apache.zest.api.unitofwork.ConcurrentEntityModificationException;
import org.apache.zest.api.unitofwork.NoSuchEntityException;
import org.apache.zest.api.unitofwork.NoSuchEntityTypeException;
import org.apache.zest.api.unitofwork.UnitOfWork;
import org.apache.zest.api.unitofwork.UnitOfWorkCallback;
import org.apache.zest.api.unitofwork.UnitOfWorkCompletionException;
import org.apache.zest.api.unitofwork.UnitOfWorkException;
import org.apache.zest.api.unitofwork.UnitOfWorkOptions;
import org.apache.zest.api.usecase.Usecase;
import org.apache.zest.runtime.entity.EntityInstance;
import org.apache.zest.runtime.entity.EntityModel;
import org.apache.zest.spi.entity.EntityState;
import org.apache.zest.spi.entity.EntityStatus;
import org.apache.zest.spi.entitystore.ConcurrentEntityStateModificationException;
import org.apache.zest.spi.entitystore.EntityNotFoundException;
import org.apache.zest.spi.entitystore.EntityStore;
import org.apache.zest.spi.entitystore.EntityStoreUnitOfWork;
import org.apache.zest.spi.entitystore.StateCommitter;
import org.apache.zest.spi.metrics.DefaultMetric;
import org.apache.zest.spi.module.ModuleSpi;

import static org.apache.zest.api.unitofwork.UnitOfWorkCallback.UnitOfWorkStatus.COMPLETED;
import static org.apache.zest.api.unitofwork.UnitOfWorkCallback.UnitOfWorkStatus.DISCARDED;

public final class UnitOfWorkInstance
{
    private static final ThreadLocal<Stack<UnitOfWorkInstance>> CURRENT = new ThreadLocal<Stack<UnitOfWorkInstance>>()
    {
        @Override
        protected Stack<UnitOfWorkInstance> initialValue()
        {
            return new Stack<>();
        }
    };

    public static Stack<UnitOfWorkInstance> getCurrent()
    {
        return CURRENT.get();
    }

    private final HashMap<EntityReference, EntityInstance> instanceCache = new HashMap<>();
    private final HashMap<EntityStore, EntityStoreUnitOfWork> storeUnitOfWork = new HashMap<>();
    private final ModuleSpi module;
    private final Usecase usecase;
    private final Instant currentTime;
    private final MetricsProvider metrics;

    private boolean open;
    private boolean paused;

    private MetricsTimer.Context metricsTimer;
    private MetaInfo metaInfo;
    private List<UnitOfWorkCallback> callbacks;

    public UnitOfWorkInstance(ModuleSpi module, Usecase usecase, Instant currentTime, MetricsProvider metrics )
    {
        this.module = module;
        this.usecase = usecase;
        this.currentTime = currentTime;
        this.metrics = metrics;

        this.open = true;
        getCurrent().push( this );
        this.paused = false;
        startCapture();
    }

    public Instant currentTime()
    {
        return currentTime;
    }

    public EntityStoreUnitOfWork getEntityStoreUnitOfWork( EntityStore store )
    {
        EntityStoreUnitOfWork uow = storeUnitOfWork.get( store );
        if( uow == null )
        {
            uow = store.newUnitOfWork( module.descriptor(), usecase, currentTime );
            storeUnitOfWork.put( store, uow );
        }
        return uow;
    }

    public <T> T get( EntityReference reference,
                      UnitOfWork uow,
                      Iterable<? extends EntityDescriptor> potentialModels,
                      Class<T> mixinType
    )
        throws NoSuchEntityTypeException, NoSuchEntityException
    {
        checkOpen();

        EntityInstance entityInstance = instanceCache.get( reference );
        if( entityInstance == null )
        {   // Not yet in cache

            // Check if this is a root UoW, or if no parent UoW knows about this entity
            EntityState entityState = null;
            EntityModel model = null;
            ModuleDescriptor module = null;
            // Figure out what EntityStore to use
            for( EntityDescriptor potentialModel : potentialModels )
            {
                EntityStore store = ((ModuleSpi) potentialModel.module().instance()).entityStore();
                EntityStoreUnitOfWork storeUow = getEntityStoreUnitOfWork( store );
                try
                {
                    entityState = storeUow.entityStateOf( potentialModel.module(), reference );
                }
                catch( EntityNotFoundException e )
                {
                    continue;
                }

                // Get the selected model
                model = (EntityModel) entityState.entityDescriptor();
                module = potentialModel.module();
            }

            // Check if model was found
            if( model == null )
            {
                // Check if state was found
                if( entityState == null )
                {
                    throw new NoSuchEntityException( reference, mixinType, usecase );
                }
                else
                {
                    throw new NoSuchEntityTypeException( mixinType.getName(), module.name(), module.typeLookup() );
                }
            }
            // Create instance
            entityInstance = new EntityInstance( uow, model, entityState );
            instanceCache.put( reference, entityInstance );
        }
        else
        {
            // Check if it has been removed
            if( entityInstance.status() == EntityStatus.REMOVED )
            {
                throw new NoSuchEntityException( reference, mixinType, usecase );
            }
        }

        return entityInstance.proxy();
    }

    public Usecase usecase()
    {
        return usecase;
    }

    public MetaInfo metaInfo()
    {
        if( metaInfo == null )
        {
            metaInfo = new MetaInfo();
        }

        return metaInfo;
    }

    public void pause()
    {
        if( !paused )
        {
            paused = true;
            getCurrent().pop();

            UnitOfWorkOptions unitOfWorkOptions = metaInfo().get( UnitOfWorkOptions.class );
            if( unitOfWorkOptions == null )
            {
                unitOfWorkOptions = usecase().metaInfo( UnitOfWorkOptions.class );
            }

            if( unitOfWorkOptions != null )
            {
                if( unitOfWorkOptions.isPruneOnPause() )
                {
                    List<EntityReference> prunedInstances = null;
                    for( EntityInstance entityInstance : instanceCache.values() )
                    {
                        if( entityInstance.status() == EntityStatus.LOADED )
                        {
                            if( prunedInstances == null )
                            {
                                prunedInstances = new ArrayList<>();
                            }
                            prunedInstances.add( entityInstance.reference() );
                        }
                    }
                    if( prunedInstances != null )
                    {
                        prunedInstances.forEach( instanceCache::remove );
                    }
                }
            }
        }
        else
        {
            throw new UnitOfWorkException( "Unit of work is not active" );
        }
    }

    public void resume()
    {
        if( paused )
        {
            paused = false;
            getCurrent().push( this );
        }
        else
        {
            throw new UnitOfWorkException( "Unit of work has not been paused" );
        }
    }

    public void complete()
        throws UnitOfWorkCompletionException
    {
        checkOpen();

        // Copy list so that it cannot be modified during completion
        List<UnitOfWorkCallback> currentCallbacks = callbacks == null ? null : new ArrayList<>( callbacks );

        // Commit state to EntityStores
        List<StateCommitter> committers = applyChanges();

        // Check callbacks
        notifyBeforeCompletion( currentCallbacks );

        // Commit all changes
        committers.forEach( StateCommitter::commit );

        close();

        // Call callbacks
        notifyAfterCompletion( currentCallbacks, COMPLETED );

        callbacks = currentCallbacks;
    }

    public void discard()
    {
        if( !isOpen() )
        {
            return;
        }
        close();

        // Copy list so that it cannot be modified during completion
        List<UnitOfWorkCallback> currentCallbacks = callbacks == null ? null : new ArrayList<>( callbacks );

        // Call callbacks
        notifyAfterCompletion( currentCallbacks, DISCARDED );
        storeUnitOfWork.values().forEach( EntityStoreUnitOfWork::discard );
        callbacks = currentCallbacks;
    }

    private void close()
    {
        checkOpen();

        if( !isPaused() )
        {
            getCurrent().pop();
        }
        endCapture();
        open = false;
    }

    public boolean isOpen()
    {
        return open;
    }

    public void addUnitOfWorkCallback( UnitOfWorkCallback callback )
    {
        if( callbacks == null )
        {
            callbacks = new ArrayList<>();
        }

        callbacks.add( callback );
    }

    public void removeUnitOfWorkCallback( UnitOfWorkCallback callback )
    {
        if( callbacks != null )
        {
            callbacks.remove( callback );
        }
    }

    public void addEntity( EntityInstance instance )
    {
        instanceCache.put( instance.reference(), instance );
    }

    private List<StateCommitter> applyChanges()
        throws UnitOfWorkCompletionException
    {
        List<StateCommitter> committers = new ArrayList<>();
        for( EntityStoreUnitOfWork entityStoreUnitOfWork : storeUnitOfWork.values() )
        {
            try
            {
                StateCommitter committer = entityStoreUnitOfWork.applyChanges();
                committers.add( committer );
            }
            catch( Exception e )
            {
                // Cancel all previously prepared stores
                committers.forEach( StateCommitter::cancel );

                if( e instanceof ConcurrentEntityStateModificationException )
                {
                    // If we cancelled due to concurrent modification, then create the proper exception for it!
                    ConcurrentEntityStateModificationException mee = (ConcurrentEntityStateModificationException) e;
                    Collection<EntityReference> modifiedEntityIdentities = mee.modifiedEntities();
                    Map<EntityComposite, HasTypes> modifiedEntities = new HashMap<>();
                    for( EntityReference modifiedEntityIdentity : modifiedEntityIdentities )
                    {
                        instanceCache.values().stream()
                            .filter( instance -> instance.reference().equals( modifiedEntityIdentity ) )
                            .forEach( instance -> modifiedEntities.put( instance.<EntityComposite>proxy(), instance ) );
                    }
                    throw new ConcurrentEntityModificationException( modifiedEntities, usecase );
                }
                else
                {
                    throw new UnitOfWorkCompletionException( e );
                }
            }
        }
        return committers;
    }

    private void notifyBeforeCompletion( List<UnitOfWorkCallback> callbacks )
        throws UnitOfWorkCompletionException
    {
        // Notify explicitly registered callbacks
        if( callbacks != null )
        {
            callbacks.forEach( UnitOfWorkCallback::beforeCompletion );
        }

        // Notify entities
        try
        {
            for( EntityInstance instance : instanceCache.values() )
            {
                boolean isCallback = instance.proxy() instanceof UnitOfWorkCallback;
                boolean isNotRemoved = !instance.status().equals( EntityStatus.REMOVED );
                if( isCallback && isNotRemoved )
                {
                    UnitOfWorkCallback callback = UnitOfWorkCallback.class.cast( instance.proxy() );
                    callback.beforeCompletion();
                }
            }
        }
        catch( UnitOfWorkCompletionException e )
        {
            throw e;
        }
        catch( Exception e )
        {
            throw new UnitOfWorkCompletionException( e );
        }
    }

    private void notifyAfterCompletion( List<UnitOfWorkCallback> callbacks,
                                        final UnitOfWorkCallback.UnitOfWorkStatus status
    )
    {
        if( callbacks != null )
        {
            for( UnitOfWorkCallback callback : callbacks )
            {
                try
                {
                    callback.afterCompletion( status );
                }
                catch( Exception e )
                {
                    // Ignore
                }
            }
        }

        // Notify entities
        try
        {
            for( EntityInstance instance : instanceCache.values() )
            {
                boolean isCallback = instance.proxy() instanceof UnitOfWorkCallback;
                boolean isNotRemoved = !instance.status().equals( EntityStatus.REMOVED );
                if( isCallback && isNotRemoved )
                {
                    UnitOfWorkCallback callback = UnitOfWorkCallback.class.cast( instance.proxy() );
                    callback.afterCompletion( status );
                }
            }
        }
        catch( Exception e )
        {
            // Ignore
        }
    }

    public void checkOpen()
    {
        if( !isOpen() )
        {
            throw new UnitOfWorkException( "Unit of work has been closed" );
        }
    }

    public boolean isPaused()
    {
        return paused;
    }

    @Override
    public String toString()
    {
        return "UnitOfWork " + hashCode() + "(" + usecase + "): entities:" + instanceCache.size();
    }

    public void remove( EntityReference entityReference )
    {
        instanceCache.remove( entityReference );
    }

    private void incrementCount()
    {
        MetricsCounter counter = getCounter();
        counter.increment();
    }

    private void decrementCount()
    {
        MetricsCounter counter = getCounter();
        counter.decrement();
    }

    private MetricsCounter getCounter()
    {
        if( metrics != null )
        {
            MetricsCounterFactory metricsFactory = metrics.createFactory( MetricsCounterFactory.class );
            return metricsFactory.createCounter( getClass(), "UnitOfWork Counter" );
        }
        return new DefaultMetric();
    }

    private void endCapture()
    {
        decrementCount();
        metricsTimer.stop();
    }

    private void startCapture()
    {
        incrementCount();
        startTimer( metrics );
    }

    private void startTimer( MetricsProvider metrics )
    {
        MetricsTimerFactory metricsFactory = metrics.createFactory( MetricsTimerFactory.class );
        String name = "UnitOfWork Timer";
        MetricsTimer timer = metricsFactory.createTimer( getClass(), name );
        metricsTimer = timer.start();
    }
}
