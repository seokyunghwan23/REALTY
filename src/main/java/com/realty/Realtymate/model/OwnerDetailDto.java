package com.realty.Realtymate.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class OwnerDetailDto {
    private String address;
    private String detailAddress;
    private String platform;
    private String owner;
    private String contact;
    private String verificationMethod;
    private String managementOffice;
    private String gender;
    private String memo;
}
