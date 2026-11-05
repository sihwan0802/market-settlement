package com.sihwan.market.member;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MemberMapper {

    @Select("INSERT INTO member (email, password_hash, name, role) "
            + "VALUES (#{email}, #{passwordHash}, #{name}, #{role}) RETURNING id")
    long insertMember(@Param("email") String email, @Param("passwordHash") String passwordHash,
                      @Param("name") String name, @Param("role") String role);

    @Select("INSERT INTO seller (member_id, store_name) VALUES (#{memberId}, #{storeName}) RETURNING id")
    long insertSeller(@Param("memberId") long memberId, @Param("storeName") String storeName);

    @Select("SELECT id, email, password_hash, name, role FROM member WHERE email = #{email}")
    Optional<Member> findByEmail(@Param("email") String email);
}
