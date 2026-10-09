import { Text, type TextProps } from 'react-native';

import { colors, fonts, type FontWeight } from '@/theme/tokens';

type Props = TextProps & { weight?: FontWeight };

export function AppText({ weight = 'regular', style, ...rest }: Props) {
  return <Text {...rest} style={[{ fontFamily: fonts[weight], color: colors.text }, style]} />;
}
