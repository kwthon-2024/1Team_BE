package kwthon_1team.kwthon.service;

import kwthon_1team.kwthon.converter.MemberConverter;
import kwthon_1team.kwthon.domian.dto.request.AuthRequestDto;
import kwthon_1team.kwthon.domian.dto.response.AuthLoginResponse;
import kwthon_1team.kwthon.domian.dto.response.SearchResponse;
import kwthon_1team.kwthon.domian.entity.EmailVerification;
import kwthon_1team.kwthon.domian.entity.Member;
import kwthon_1team.kwthon.exception.BaseException;
import kwthon_1team.kwthon.repository.EmailVerificationRepository;
import kwthon_1team.kwthon.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final EmailService emailService;
    private final EmailVerificationRepository emailVerificationRepository;
    private final MemberConverter memberConverter;

    @Transactional
    public void join(AuthRequestDto authRequestDto) {
        validateAuthRequest(authRequestDto);
        checkDuplicateEmail(authRequestDto.getEmail());
        memberRepository.save(converToMember(authRequestDto));
    }

    @Transactional
    public void checkDuplicateEmail(String email) {
        String trimmedEmail = email.trim();
        if (!memberRepository.findByEmail(trimmedEmail).stream().toList().isEmpty()) {
            throw new BaseException(400, "이미 회원가입된 이메일");
        }
        validateUniversityEmail(trimmedEmail);

        requestEmailVerification(trimmedEmail);
    }

    @Transactional (readOnly = true)
    public void validateUniversityEmail(String email) {
        if (!email.endsWith("@naver.com")) {
            throw new BaseException(400, "광운대학교 이메일(@kw.ac.kr)만 입력해 주세요.");
        }
    }

    private void validateAuthRequest(AuthRequestDto authRequestDto) {
        if (authRequestDto.getStudentId() == null)
            throw new BaseException(400, "아이디를 입력해 주세요.");
        if (authRequestDto.getDepartment() == null)
            throw new BaseException(400, "학과를 입력해 주세요.");
        if (authRequestDto.getPassword() == null)
            throw new BaseException(400, "비밀번호를 입력해 주세요.");
        if (authRequestDto.getName() == null)
            throw new BaseException(400, "이름을 입력해 주세요.");
        if (authRequestDto.getEmail() == null)
            throw new BaseException(400, "이메일을 입력해 주세요.");
        if (!authRequestDto.isAgreement())
            throw new BaseException(400, "개인정보 수집에 동의해 주세요.");
    }

    private Member converToMember(AuthRequestDto authRequestDto) {
        return new Member(authRequestDto);
    }

    @Transactional
    public void requestEmailVerification(String email) {
        // 인증 코드 생성 및 저장
        Integer verificationCode = generateVerificationCode();
        LocalDateTime expirationDate = LocalDateTime.now().plusMinutes(10);  // 유효 기간 10분
        EmailVerification emailVerification = new EmailVerification(email, verificationCode, expirationDate);
        emailVerificationRepository.save(emailVerification);

        emailService.sendValidateEmailRequestMessage(email, verificationCode.toString());
    }

    private Integer generateVerificationCode() {
        return (int)(Math.random() * 1000000);
    }

    @Transactional
    public void verifyEmailCode(String email, Integer code) {

        EmailVerification emailVerification = emailVerificationRepository.findLatestByEmailNative(email)
                .orElseThrow(()->new BaseException(400, "이메일 인증이 필요합니다."));

        // 인증 코드 유효 기간 검증
        if (emailVerification.getExpirationTime().isBefore(LocalDateTime.now())) {
            throw new BaseException(400, "인증번호가 만료되었습니다.");
        }

        if (emailVerification.getVerificationCode() == null ||
                !emailVerification.getVerificationCode().equals(code)) {
            throw new BaseException(400, "인증번호가 일치하지 않습니다.");
        }
        emailVerificationRepository.delete(emailVerification);
        emailVerificationRepository.flush();
    }

    /*private void saveEmailToSession(String email) {
        HttpSession session = getHttpSession();
        session.setAttribute("email", email);
        System.out.println("이메일이 세션에 저장되었습니다: " + email);
    }

    private HttpSession getHttpSession() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attr.getRequest().getSession(true);
    }

    private String getEmailFromSession() {
        HttpSession session = getHttpSession();
        String email = (String) session.getAttribute("email");
        if (email == null) {
            throw new BaseException(400, "이메일 세션이 존재하지 않습니다.");
        }
        return email;
    }*/

    @Transactional
    public AuthLoginResponse login(Long studentId, String password) {
        Member member = getMemberById(studentId);
        member.validatePassword(password);
        return new AuthLoginResponse(member.getStudentId());
    }

    private Member getMemberById(Long studentId) {
        return memberRepository.findByStudentId(studentId).stream().findFirst()
                .orElseThrow(()->new BaseException(404, "회원가입되지 않은 이메일"));
    }

    public List<SearchResponse> search(String keyword) {
        List<Member> memberList = memberRepository.findAllByKeyword(keyword);
        return memberList.stream().map(memberConverter::toSearchResponse).toList();
    }
}
