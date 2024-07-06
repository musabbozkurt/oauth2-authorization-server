package mb.oauth2authorizationserver.mapper;

import mb.oauth2authorizationserver.api.request.ApiUserRequest;
import mb.oauth2authorizationserver.api.response.ApiUserResponse;
import mb.oauth2authorizationserver.data.entity.SecurityUser;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    ApiUserResponse map(SecurityUser user);

    List<ApiUserResponse> map(List<SecurityUser> users);

    SecurityUser map(ApiUserRequest apiUserRequest);

    default SecurityUser map(SecurityUser oldRecord, SecurityUser newRecord) {
        oldRecord.setFirstName(StringUtils.isNotBlank(newRecord.getFirstName()) ? newRecord.getFirstName() : oldRecord.getFirstName());
        oldRecord.setLastName(StringUtils.isNotBlank(newRecord.getLastName()) ? newRecord.getLastName() : oldRecord.getLastName());
        oldRecord.setUsername(StringUtils.isNotBlank(newRecord.getUsername()) ? newRecord.getUsername() : oldRecord.getUsername());
        oldRecord.setEmail(StringUtils.isNotBlank(newRecord.getEmail()) ? newRecord.getEmail() : oldRecord.getEmail());
        oldRecord.setPhoneNumber(StringUtils.isNotBlank(newRecord.getPhoneNumber()) ? newRecord.getPhoneNumber() : oldRecord.getPhoneNumber());
        return oldRecord;
    }

    default Page<ApiUserResponse> map(Page<SecurityUser> orders) {
        return orders.map(this::map);
    }
}
