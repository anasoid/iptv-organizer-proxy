package org.anasoid.iptvorganizer.models.filtering;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterConfig {
    private List<FilterRule> rules;
}
