package mb.oauth2authorizationserver.config.service;

public interface GenericBuilderService<T, E> {

    T toObject(E entity);


    E toEntity(T object);
}
