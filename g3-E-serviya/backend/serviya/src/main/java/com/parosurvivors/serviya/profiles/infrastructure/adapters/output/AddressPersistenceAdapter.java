package com.parosurvivors.serviya.profiles.infrastructure.adapters.output;

import com.parosurvivors.serviya.profiles.application.ports.output.AddressPersistencePort;
import com.parosurvivors.serviya.profiles.domain.Address;
import com.parosurvivors.serviya.profiles.infrastructure.entities.AddressEntity;
import com.parosurvivors.serviya.profiles.infrastructure.mappers.AddressPersistenceMapper;
import com.parosurvivors.serviya.profiles.infrastructure.repositories.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AddressPersistenceAdapter implements AddressPersistencePort {

    private final AddressRepository repository;
    private final AddressPersistenceMapper mapper;

    @Override
    public Address save(Address address) {
        AddressEntity saved = repository.save(mapper.toEntity(address));
        return mapper.toDomain(saved);
    }

    @Override
    public Address update(Address address) {
        AddressEntity updated = repository.save(mapper.toEntity(address));
        return mapper.toDomain(updated);
    }

    @Override
    public Optional<Address> findById(Long id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Address> findByUserId(Long userId) {
        return repository.findByUserId(userId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
