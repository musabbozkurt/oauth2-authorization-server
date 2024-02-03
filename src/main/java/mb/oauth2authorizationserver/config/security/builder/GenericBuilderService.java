package mb.oauth2authorizationserver.config.security.builder;

public interface GenericBuilderService<T, E> {

    T toObject(E entity);


    E toEntity(T object);
}
