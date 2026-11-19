package com.sihwan.market.member;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Select("SELECT id, email, password_hash, name, role FROM member WHERE id = #{id} AND withdrawn_at IS NULL")
    Optional<Member> findActiveById(@Param("id") long id);

    @Update("UPDATE member SET password_hash = #{hash} WHERE id = #{id} AND withdrawn_at IS NULL")
    int updatePassword(@Param("id") long id, @Param("hash") String hash);

    // 행은 지우지 않고 개인정보만 비운다. 과거 주문, 결제, 정산은 member_id로 계속 이어진다
    @Update("UPDATE member SET email = 'withdrawn-' || id || '@withdrawn.invalid', name = '탈퇴회원', "
            + "password_hash = '!', withdrawn_at = now() WHERE id = #{id} AND withdrawn_at IS NULL")
    int withdraw(@Param("id") long id);
}
