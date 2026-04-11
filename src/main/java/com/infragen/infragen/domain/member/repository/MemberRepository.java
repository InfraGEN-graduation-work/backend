package com.infragen.infragen.domain.member.repository;

import com.infragen.infragen.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}
