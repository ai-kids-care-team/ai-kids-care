package com.ai_kids_care.v1.mapper;

import com.ai_kids_care.v1.dto.RoomCreateDTO;
import com.ai_kids_care.v1.dto.RoomUpdateDTO;
import com.ai_kids_care.v1.entity.Room;
import com.ai_kids_care.v1.vo.RoomVO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface RoomMapper {

    @Mapping(target = "roomId", ignore = true)
    @Mapping(source = "kindergarten.id", target = "kindergartenId")
    RoomVO toVO(Room entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Room toEntity(RoomCreateDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "kindergartenId", target = "kindergarten.id")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(RoomUpdateDTO dto, @MappingTarget Room entity);
}