package org.anasoid.iptvorganizer.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Source extends BaseEntity {
    private String name;
    private String url;
    private String username;
    private String password;
    private Integer syncInterval;
    private LocalDateTime lastSync;
    private LocalDateTime nextSync;
    private Boolean isActive;
    private Boolean enableProxy;
    private Boolean disableStreamProxy;
    private Boolean streamFollowLocation;
}
