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

package org.apache.polygene.runtime.unitofwork;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.polygene.api.PolygeneAPI;
import org.apache.polygene.api.association.AssociationDescriptor;
import org.apache.polygene.api.association.AssociationStateHolder;
import org.apache.polygene.api.association.ManyAssociation;
import org.apache.polygene.api.association.NamedAssociation;
import org.apache.polygene.api.composite.Composite;
import org.apache.polygene.api.entity.EntityBuilder;
import org.apache.polygene.api.entity.EntityComposite;
import org.apache.polygene.api.entity.EntityDescriptor;
import org.apache.polygene.api.entity.EntityReference;
import org.apache.polygene.api.entity.LifecycleException;
import org.apache.polygene.api.identity.HasIdentity;
import org.apache.polygene.api.identity.Identity;
import org.apache.polygene.api.identity.IdentityGenerator;
import org.apache.polygene.api.identity.StringIdentity;
import org.apache.polygene.api.injection.scope.Service;
import org.apache.polygene.api.injection.scope.Structure;
import org.apache.polygene.api.injection.scope.Uses;
import org.apache.polygene.api.property.Property;
import org.apache.polygene.api.property.PropertyDescriptor;
import org.apache.polygene.api.property.StateHolder;
import org.apache.polygene.api.query.Query;
import org.apache.polygene.api.query.QueryBuilder;
import org.apache.polygene.api.query.QueryExecutionException;
import org.apache.polygene.api.query.grammar.OrderBy;
import org.apache.polygene.api.service.NoSuchServiceException;
import org.apache.polygene.api.structure.ModuleDescriptor;
import org.apache.polygene.api.unitofwork.ConcurrentEntityModificationException;
import org.apache.polygene.api.unitofwork.NoSuchEntityException;
import org.apache.polygene.api.unitofwork.NoSuchEntityTypeException;
import org.apache.polygene.api.unitofwork.UnitOfWork;
import org.apache.polygene.api.unitofwork.UnitOfWorkCallback;
import org.apache.polygene.api.unitofwork.UnitOfWorkCompletionException;
import org.apache.polygene.api.unitofwork.UnitOfWorkFactory;
import org.apache.polygene.api.usecase.Usecase;
import org.apache.polygene.api.value.ValueBuilder;
import org.apache.polygene.api.value.ValueComposite;
import org.apache.polygene.runtime.association.AssociationInstance;
import org.apache.polygene.runtime.composite.FunctionStateResolver;
import org.apache.polygene.runtime.entity.EntityInstance;
import org.apache.polygene.runtime.entity.EntityModel;
import org.apache.polygene.runtime.property.PropertyModel;
import org.apache.polygene.runtime.value.ValueInstance;
import org.apache.polygene.spi.entity.EntityState;
import org.apache.polygene.spi.entity.EntityStatus;
import org.apache.polygene.spi.entitystore.EntityStore;
import org.apache.polygene.spi.module.ModuleSpi;
import org.apache.polygene.spi.query.EntityFinder;
import org.apache.polygene.spi.query.EntityFinderException;
import org.apache.polygene.spi.query.QueryBuilderSPI;
import org.apache.polygene.spi.query.QuerySource;

import static org.apache.polygene.api.identity.HasIdentity.IDENTITY_STATE_NAME;

/**
 * JAVADOC
 */
public class ModuleUnitOfWork
    implements UnitOfWork
{
    @Uses
    private UnitOfWorkInstance uow;

    @Structure
    private PolygeneAPI api;

    @Structure
    private ModuleDescriptor module;

    @Service
    private UnitOfWorkFactory unitOfWorkFactory;

    public ModuleDescriptor module()
    {
        return module;
    }

    public UnitOfWorkInstance instance()
    {
        return uow;
    }

    @Override
    public UnitOfWorkFactory unitOfWorkFactory()
    {
        return unitOfWorkFactory;
    }

    @Override
    public Instant currentTime()
    {
        return uow.currentTime();
    }

    @Override
    public Usecase usecase()
    {
        return uow.usecase();
    }

    @Override
    public <T> T metaInfo( Class<T> infoType )
    {
        return uow.metaInfo().get( infoType );
    }

    @Override
    public void setMetaInfo( Object metaInfo )
    {
        uow.metaInfo().set( metaInfo );
    }

    @Override
    @SuppressWarnings( { "raw", "unchecked" } )
    public <T> Query<T> newQuery( QueryBuilder<T> queryBuilder )
    {
        QueryBuilderSPI queryBuilderSPI = (QueryBuilderSPI) queryBuilder;

        return queryBuilderSPI.newQuery( new UoWQuerySource( this ) );
    }

    @Override
    public <T> T newEntity( Class<T> type )
        throws NoSuchEntityTypeException, LifecycleException
    {
        return newEntity( type, null );
    }

    @Override
    public <T> T newEntity( Class<T> type, Identity identity )
        throws NoSuchEntityTypeException, LifecycleException
    {
        return newEntityBuilder( type, identity ).newInstance();
    }

    @Override
    public <T> EntityBuilder<T> newEntityBuilder( Class<T> type )
        throws NoSuchEntityTypeException
    {
        return newEntityBuilder( type, null );
    }

    @Override
    public <T> EntityBuilder<T> newEntityBuilder( Class<T> type, Identity identity )
        throws NoSuchEntityTypeException
    {
        EntityDescriptor model = module.typeLookup().lookupEntityModel( type );

        if( model == null )
        {
            throw new NoSuchEntityTypeException( type.getName(), module.name(), module.typeLookup() );
        }

        ModuleDescriptor modelModule = model.module();
        EntityStore entityStore = ( (ModuleSpi) modelModule.instance() ).entityStore();

        // Generate id if necessary
        if( identity == null )
        {
            IdentityGenerator idGen = ( (ModuleSpi) modelModule.instance() ).identityGenerator();
            if( idGen == null )
            {
                throw new NoSuchServiceException( IdentityGenerator.class.getName(), modelModule
                    .name(), modelModule.typeLookup() );
            }
            identity = idGen.generate( model.types().findFirst().orElse( null ) );
        }
        EntityBuilder<T> builder;

        builder = new EntityBuilderInstance<>( model,
                                               this,
                                               uow.getEntityStoreUnitOfWork( entityStore ),
                                               identity );
        return builder;
    }

    @Override
    public <T> EntityBuilder<T> newEntityBuilderWithState(
        Class<T> type,
        Function<PropertyDescriptor, Object> propertyFunction,
        Function<AssociationDescriptor, EntityReference> associationFunction,
        Function<AssociationDescriptor, Stream<EntityReference>> manyAssociationFunction,
        Function<AssociationDescriptor, Stream<Map.Entry<String, EntityReference>>> namedAssociationFunction
    )
        throws NoSuchEntityTypeException
    {
        return newEntityBuilderWithState( type, null,
                                          propertyFunction,
                                          associationFunction,
                                          manyAssociationFunction,
                                          namedAssociationFunction );
    }

    @Override
    public <T> EntityBuilder<T> newEntityBuilderWithState(
        Class<T> type, Identity identity,
        Function<PropertyDescriptor, Object> propertyFunction,
        Function<AssociationDescriptor, EntityReference> associationFunction,
        Function<AssociationDescriptor, Stream<EntityReference>> manyAssociationFunction,
        Function<AssociationDescriptor, Stream<Map.Entry<String, EntityReference>>> namedAssociationFunction
    )
        throws NoSuchEntityTypeException
    {
        Objects.requireNonNull( propertyFunction, "propertyFunction" );
        Objects.requireNonNull( associationFunction, "associationFunction" );
        Objects.requireNonNull( manyAssociationFunction, "manyAssociationFunction" );
        Objects.requireNonNull( namedAssociationFunction, "namedAssociationFunction" );

        EntityDescriptor model = module.typeLookup().lookupEntityModel( type );

        if( model == null )
        {
            throw new NoSuchEntityTypeException( type.getName(), module.name(), module.typeLookup() );
        }

        ModuleDescriptor modelModule = model.module();
        ModuleSpi moduleSpi = (ModuleSpi) modelModule.instance();
        EntityStore entityStore = moduleSpi.entityStore();

        FunctionStateResolver stateResolver = new FunctionStateResolver(
            propertyFunction, associationFunction, manyAssociationFunction, namedAssociationFunction
        );

        if( identity == null )
        {
            // Use reference from StateResolver if available
            PropertyModel identityModel = (PropertyModel) model
                .state()
                .findPropertyModelByQualifiedName( IDENTITY_STATE_NAME );
            String propertyState = (String) stateResolver.getPropertyState(identityModel);
            if( propertyState == null )
            {
                // Generate reference
                IdentityGenerator idGen = moduleSpi.identityGenerator();
                if( idGen == null )
                {
                    String typeName = IdentityGenerator.class.getName();
                    throw new NoSuchServiceException( typeName, modelModule.name(), modelModule.typeLookup() );
                }
                identity = idGen.generate( model.types().findFirst().orElse( null ) );
            }
            else
            {
                identity = new StringIdentity(propertyState);
            }
        }

        return new EntityBuilderInstance<>( model,
                                            this,
                                            uow.getEntityStoreUnitOfWork( entityStore ),
                                            identity,
                                            stateResolver );
    }

    @Override
    public <T> T get( Class<T> type, Identity identity )
        throws NoSuchEntityTypeException, NoSuchEntityException
    {
        Iterable<EntityDescriptor> models = module.typeLookup().lookupEntityModels( type );

        if( !models.iterator().hasNext() )
        {
            throw new NoSuchEntityTypeException( type.getName(), module.name(), module.typeLookup() );
        }

        return uow.get( EntityReference.create( identity ), this, models, type );
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public <T> T get( T entity )
        throws NoSuchEntityTypeException
    {
        EntityComposite entityComposite = (EntityComposite) entity;
        EntityInstance compositeInstance = EntityInstance.entityInstanceOf( entityComposite );
        EntityDescriptor model = compositeInstance.entityModel();
        Class<T> type = (Class<T>) compositeInstance.types().findFirst().orElse( null );
        return uow.get( compositeInstance.reference(), this, Collections.singletonList( model ), type );
    }

    @Override
    public void remove( Object entity )
        throws LifecycleException
    {
        uow.checkOpen();

        EntityComposite entityComposite = (EntityComposite) entity;

        EntityInstance compositeInstance = EntityInstance.entityInstanceOf( entityComposite );

        if( compositeInstance.status() == EntityStatus.NEW )
        {
            compositeInstance.remove( this );
            uow.remove( compositeInstance.reference() );
        }
        else if( compositeInstance.status() == EntityStatus.LOADED || compositeInstance.status() == EntityStatus.UPDATED )
        {
            compositeInstance.remove( this );
        }
        else
        {
            throw new NoSuchEntityException( compositeInstance.reference(), compositeInstance.types(), usecase() );
        }
    }

    @SuppressWarnings( "DuplicateThrows" )
    @Override
    public void complete()
        throws UnitOfWorkCompletionException, ConcurrentEntityModificationException
    {
        uow.complete();
    }

    @Override
    public void discard()
    {
        uow.discard();
    }

    @Override
    public void close()
    {
        discard();
    }

    @Override
    public boolean isOpen()
    {
        return uow.isOpen();
    }

    @Override
    public boolean isPaused()
    {
        return uow.isPaused();
    }

    @Override
    public void pause()
    {
        uow.pause();
    }

    @Override
    public void resume()
    {
        uow.resume();
    }

    @Override
    public void addUnitOfWorkCallback( UnitOfWorkCallback callback )
    {
        uow.addUnitOfWorkCallback( callback );
    }

    @Override
    public void removeUnitOfWorkCallback( UnitOfWorkCallback callback )
    {
        uow.removeUnitOfWorkCallback( callback );
    }

    @Override
    public boolean equals( Object o )
    {
        if( this == o )
        {
            return true;
        }
        if( o == null || getClass() != o.getClass() )
        {
            return false;
        }

        ModuleUnitOfWork that = (ModuleUnitOfWork) o;

        return uow.equals( that.uow );
    }

    @Override
    public int hashCode()
    {
        return uow.hashCode();
    }

    @Override
    public String toString()
    {
        return uow.toString();
    }

    public void addEntity( EntityInstance instance )
    {
        uow.addEntity( instance );
    }

    @Override
    public <T extends HasIdentity> T toValue(Class<T> primaryType, T entityComposite )
    {
        Function<PropertyDescriptor, Object> propertyFunction = new ToValuePropertyMappingFunction( entityComposite );
        Function<AssociationDescriptor, EntityReference> assocationFunction = new ToValueAssociationMappingFunction<>( entityComposite );
        Function<AssociationDescriptor, Stream<EntityReference>> manyAssocFunction = new ToValueManyAssociationMappingFunction<>( entityComposite );
        Function<AssociationDescriptor, Stream<Map.Entry<String, EntityReference>>> namedAssocFunction = new ToValueNameAssociationMappingFunction<>( entityComposite );

        @SuppressWarnings( "unchecked" )
        ValueBuilder<T> builder = module().instance().newValueBuilderWithState(
            primaryType, propertyFunction, assocationFunction, manyAssocFunction, namedAssocFunction );
        return builder.newInstance();
    }

    @Override
    public <T extends HasIdentity> Map<String, T> toValueMap(NamedAssociation<T> association )
    {
        @SuppressWarnings( "unchecked" )
        Class<T> primaryType = (Class<T>) api.associationDescriptorFor( association ).type();

        return association
            .toMap()
            .entrySet()
            .stream()
            .collect( Collectors.toMap( Map.Entry::getKey, entry -> toValue( primaryType, entry.getValue()) ) );
    }

    @Override
    public <T extends HasIdentity> List<T> toValueList(ManyAssociation<T> association )
    {
        @SuppressWarnings( "unchecked" )
        Class<T> primaryType = (Class<T>) api.associationDescriptorFor( association ).type();

        return association
            .toList()
            .stream()
            .map( entity -> toValue( primaryType, entity ) )
            .collect( Collectors.toList() );
    }

    @Override
    public <T extends HasIdentity> Set<T> toValueSet(ManyAssociation<T> association )
    {
        @SuppressWarnings( "unchecked" )
        Class<T> primaryType = (Class<T>) api.associationDescriptorFor( association ).type();

        return association
            .toSet()
            .stream()
            .map( entity -> toValue( primaryType, entity ) )
            .collect( Collectors.toSet() );
    }

    @Override
    public <T extends HasIdentity> T toEntity(Class<T> primaryType, T valueComposite )
    {
        Function<PropertyDescriptor, Object> propertyFunction = new ToEntityPropertyMappingFunction<>( valueComposite );
        Function<AssociationDescriptor, EntityReference> assocationFunction = new ToEntityAssociationMappingFunction<>( valueComposite );
        Function<AssociationDescriptor, Stream<EntityReference>> manyAssocFunction = new ToEntityManyAssociationMappingFunction<>( valueComposite );
        Function<AssociationDescriptor, Stream<Map.Entry<String, EntityReference>>> namedAssocFunction = new ToEntityNameAssociationMappingFunction<>( valueComposite );

        try
        {
            T entity = get( primaryType, valueComposite.identity().get() );
            // If successful, then this entity is to by modified.
            EntityInstance instance = EntityInstance.entityInstanceOf( (EntityComposite) entity );
            EntityState state = instance.entityState();
            FunctionStateResolver stateResolver = new FunctionStateResolver( propertyFunction,
                                                                             assocationFunction,
                                                                             manyAssocFunction,
                                                                             namedAssocFunction );
            EntityModel model = (EntityModel) EntityInstance.entityInstanceOf( (EntityComposite) entity ).descriptor();
            stateResolver.populateState( model, state );
            return entity;
        }
        catch( NoSuchEntityException e )
        {
            EntityBuilder<T> entityBuilder = newEntityBuilderWithState( primaryType,
                                                                        valueComposite.identity().get(),
                                                                        propertyFunction,
                                                                        assocationFunction,
                                                                        manyAssocFunction,
                                                                        namedAssocFunction );
            return entityBuilder.newInstance();
        }
    }

    private static class UoWQuerySource implements QuerySource
    {
        private final ModuleUnitOfWork moduleUnitOfWork;

        private UoWQuerySource( ModuleUnitOfWork moduleUnitOfWork )
        {
            this.moduleUnitOfWork = moduleUnitOfWork;
        }

        @Override
        public <T> T find( Class<T> resultType,
                           Predicate<Composite> whereClause,
                           List<OrderBy> orderBySegments,
                           Integer firstResult,
                           Integer maxResults,
                           Map<String, Object> variables
        )
        {
            final EntityFinder entityFinder = moduleUnitOfWork.module()
                .instance()
                .findService( EntityFinder.class )
                .get();

            try
            {
                EntityReference foundEntity = entityFinder.findEntity( resultType, whereClause, variables == null ? Collections.emptyMap() : variables );
                if( foundEntity != null )
                {
                    try
                    {
                        return moduleUnitOfWork.get( resultType, foundEntity.identity() );
                    }
                    catch( NoSuchEntityException e )
                    {
                        return null; // Index is out of sync - entity has been removed
                    }
                }
                // No entity was found
                return null;
            }
            catch( EntityFinderException e )
            {
                throw new QueryExecutionException( "Finder caused exception", e );
            }
        }

        @Override
        public <T> long count( Class<T> resultType,
                               Predicate<Composite> whereClause,
                               List<OrderBy> orderBySegments,
                               Integer firstResult,
                               Integer maxResults,
                               Map<String, Object> variables
        )
        {
            EntityFinder entityFinder = moduleUnitOfWork.module().instance().findService( EntityFinder.class ).get();

            try
            {
                return entityFinder.countEntities( resultType, whereClause, variables == null ? Collections.emptyMap() : variables );
            }
            catch( EntityFinderException e )
            {
                e.printStackTrace();
                return 0;
            }
        }

        @Override
        public <T> Stream<T> stream( Class<T> resultType,
                                     Predicate<Composite> whereClause,
                                     List<OrderBy> orderBySegments,
                                     Integer firstResult,
                                     Integer maxResults,
                                     Map<String, Object> variables )
        {
            EntityFinder entityFinder = moduleUnitOfWork.module().instance().findService( EntityFinder.class ).get();

            try
            {
                return entityFinder.findEntities(
                    resultType,
                    whereClause,
                    orderBySegments,
                    firstResult,
                    maxResults,
                    variables == null ? Collections.emptyMap() : variables
                ).map( ref ->
                       {
                           try
                           {
                               return moduleUnitOfWork.get( resultType, ref.identity() );
                           }
                           catch( NoSuchEntityException e )
                           {
                               // Index is out of sync - entity has been removed
                               return null;
                           }
                       } );
            }
            catch( EntityFinderException e )
            {
                throw new QueryExecutionException( "Query '" + toString() + "' could not be executed", e );
            }
        }
    }

    private class ToValuePropertyMappingFunction
        implements Function<PropertyDescriptor, Object>
    {
        private Object entity;

        public ToValuePropertyMappingFunction( Object entity )
        {
            this.entity = entity;
        }

        @Override
        public Object apply( PropertyDescriptor propertyDescriptor )
        {
            EntityState entityState = EntityInstance.entityInstanceOf( (EntityComposite) entity ).entityState();
            return entityState.propertyValueOf( propertyDescriptor.qualifiedName() );
        }
    }

    private class ToValueAssociationMappingFunction<T>
        implements Function<AssociationDescriptor, EntityReference>
    {
        private final T entity;

        public ToValueAssociationMappingFunction( T entity )
        {
            this.entity = entity;
        }

        @Override
        public EntityReference apply( AssociationDescriptor associationDescriptor )
        {
            EntityState entityState = EntityInstance.entityInstanceOf( (EntityComposite) entity ).entityState();
            return entityState.associationValueOf( associationDescriptor.qualifiedName() );
        }
    }

    private class ToValueManyAssociationMappingFunction<T>
        implements Function<AssociationDescriptor, Stream<EntityReference>>
    {
        private final T entity;

        public ToValueManyAssociationMappingFunction( T entity )
        {
            this.entity = entity;
        }

        @Override
        public Stream<EntityReference> apply( AssociationDescriptor associationDescriptor )
        {
            EntityState entityState = EntityInstance.entityInstanceOf( (EntityComposite) entity ).entityState();
            return entityState.manyAssociationValueOf( associationDescriptor.qualifiedName() ).stream();
        }
    }

    private class ToValueNameAssociationMappingFunction<T>
        implements Function<AssociationDescriptor, Stream<Map.Entry<String, EntityReference>>>
    {
        private final T entity;

        public ToValueNameAssociationMappingFunction( T entity )
        {
            this.entity = entity;
        }

        @Override
        public Stream<Map.Entry<String, EntityReference>> apply( AssociationDescriptor associationDescriptor )
        {
            EntityState entityState = EntityInstance.entityInstanceOf( (EntityComposite) entity ).entityState();
            return entityState.namedAssociationValueOf( associationDescriptor.qualifiedName() ).stream();
        }
    }

    private class ToEntityPropertyMappingFunction<T>
        implements Function<PropertyDescriptor, Object>
    {
        private final T value;

        public ToEntityPropertyMappingFunction( T value )
        {
            this.value = value;
        }

        @Override
        public Object apply( PropertyDescriptor propertyDescriptor )
        {
            StateHolder state = ValueInstance.valueInstanceOf( (ValueComposite) value ).state();
            Property<Object> property = state.propertyFor( propertyDescriptor.accessor() );
            return property.get();
        }
    }

    private class ToEntityAssociationMappingFunction<T>
        implements Function<AssociationDescriptor, EntityReference>
    {

        private final T value;

        public ToEntityAssociationMappingFunction( T value )
        {
            this.value = value;
        }

        @Override
        public EntityReference apply( AssociationDescriptor associationDescriptor )
        {
            AssociationStateHolder state = ValueInstance.valueInstanceOf( (ValueComposite) value ).state();
            AssociationInstance<T> association = (AssociationInstance<T>) state.associationFor( associationDescriptor.accessor() );
            return association.getAssociationState().get();
        }
    }

    private class ToEntityManyAssociationMappingFunction<T>
        implements Function<AssociationDescriptor, Stream<EntityReference>>
    {

        private final T value;

        public ToEntityManyAssociationMappingFunction( T valueComposite )
        {
            this.value = valueComposite;
        }

        @Override
        public Stream<EntityReference> apply( AssociationDescriptor associationDescriptor )
        {
            ValueInstance valueInstance = ValueInstance.valueInstanceOf( (ValueComposite) value );
            return valueInstance.state().manyAssociationFor( associationDescriptor.accessor() ).references();
        }
    }

    private class ToEntityNameAssociationMappingFunction<T>
        implements Function<AssociationDescriptor, Stream<Map.Entry<String, EntityReference>>>
    {
        private final T value;

        public ToEntityNameAssociationMappingFunction( T valueComposite )
        {
            this.value = valueComposite;
        }

        @Override
        public Stream<Map.Entry<String, EntityReference>> apply( AssociationDescriptor associationDescriptor )
        {
            ValueInstance valueInstance = ValueInstance.valueInstanceOf( (ValueComposite) value );
            return valueInstance.state().namedAssociationFor( associationDescriptor.accessor() ).references();
        }
    }
}